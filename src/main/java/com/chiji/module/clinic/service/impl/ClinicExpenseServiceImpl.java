package com.chiji.module.clinic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.ClinicExpense;
import com.chiji.entity.ClinicExpenseCategory;
import com.chiji.module.clinic.dto.ExpenseSaveRequest;
import com.chiji.module.clinic.mapper.ClinicExpenseCategoryMapper;
import com.chiji.module.clinic.mapper.ClinicExpenseMapper;
import com.chiji.module.clinic.service.ClinicExpenseService;
import com.chiji.module.clinic.vo.ClinicExpenseVO;
import com.chiji.module.clinic.vo.ExpenseStatsVO;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 矫正花费流水服务实现。
 * <p>
 * 统计聚合在内存完成（个人流水量级小，避免维护聚合 XML）；
 * 金额一律 BigDecimal 两位小数；分类名解析一次查全量转 Map。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicExpenseServiceImpl implements ClinicExpenseService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    /** 月度趋势回看月数（含当月） */
    private static final int TREND_MONTHS = 12;

    private final ClinicExpenseMapper expenseMapper;
    private final ClinicExpenseCategoryMapper categoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicExpenseVO create(Long userId, ExpenseSaveRequest req) {
        ClinicExpense expense = new ClinicExpense();
        apply(userId, expense, req);
        expenseMapper.insert(expense);
        log.info("新增花费流水, userId={}, id={}, amount={}", userId, expense.getId(), expense.getAmount());
        return toVO(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicExpenseVO update(Long userId, Long id, ExpenseSaveRequest req) {
        ClinicExpense expense = requireOwned(userId, id);
        apply(userId, expense, req);
        expenseMapper.updateById(expense);
        return toVO(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        ClinicExpense expense = requireOwned(userId, id);
        expenseMapper.deleteById(expense.getId());
        log.info("删除花费流水, userId={}, id={}", userId, id);
    }

    @Override
    public List<ClinicExpenseVO> list(Long userId, String month, Long categoryId) {
        LambdaQueryWrapper<ClinicExpense> wrapper = new LambdaQueryWrapper<ClinicExpense>()
                .eq(ClinicExpense::getUserId, userId);
        if (month != null && !month.isBlank()) {
            YearMonth ym;
            try {
                ym = YearMonth.parse(month.trim(), MONTH_FMT);
            } catch (RuntimeException e) {
                throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "月份格式应为 yyyy-MM");
            }
            wrapper.ge(ClinicExpense::getExpenseDate, ym.atDay(1))
                    .le(ClinicExpense::getExpenseDate, ym.atEndOfMonth());
        }
        if (categoryId != null) {
            wrapper.eq(ClinicExpense::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(ClinicExpense::getExpenseDate)
                .orderByDesc(ClinicExpense::getId);
        Map<Long, String> names = categoryNameMap(userId);
        return expenseMapper.selectList(wrapper).stream()
                .map(e -> new ClinicExpenseVO(e.getId(), e.getCategoryId(),
                        names.getOrDefault(e.getCategoryId(), "已删分类"),
                        e.getVisitId(), e.getExpenseDate(), e.getAmount(), e.getNote()))
                .toList();
    }

    @Override
    public ExpenseStatsVO stats(Long userId) {
        List<ClinicExpense> all = expenseMapper.selectList(new LambdaQueryWrapper<ClinicExpense>()
                .eq(ClinicExpense::getUserId, userId));
        Map<Long, String> names = categoryNameMap(userId);

        // 分类小计（金额降序）
        Map<Long, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ClinicExpense e : all) {
            byCategory.merge(e.getCategoryId(), e.getAmount(), BigDecimal::add);
        }
        BigDecimal total = byCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExpenseStatsVO.CategoryStat> categoryStats = byCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .map(en -> new ExpenseStatsVO.CategoryStat(
                        en.getKey(),
                        names.getOrDefault(en.getKey(), "已删分类"),
                        en.getValue(),
                        total.signum() > 0
                                ? en.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(total, 1, RoundingMode.HALF_UP).doubleValue()
                                : 0d))
                .toList();

        // 近 12 个月趋势（含当月，升序）
        YearMonth current = YearMonth.from(WearTimes.today());
        Map<String, BigDecimal> monthlyMap = new LinkedHashMap<>();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            monthlyMap.put(current.minusMonths(i).format(MONTH_FMT), BigDecimal.ZERO);
        }
        for (ClinicExpense e : all) {
            String key = YearMonth.from(e.getExpenseDate()).format(MONTH_FMT);
            if (monthlyMap.containsKey(key)) {
                monthlyMap.merge(key, e.getAmount(), BigDecimal::add);
            }
        }
        List<ExpenseStatsVO.MonthAmount> monthly = monthlyMap.entrySet().stream()
                .map(en -> new ExpenseStatsVO.MonthAmount(en.getKey(), en.getValue()))
                .toList();

        return new ExpenseStatsVO(total, categoryStats, monthly);
    }

    /** 实体 → VO（分类名实时解析，悬空分类兜底「已删分类」）。 */
    private ClinicExpenseVO toVO(ClinicExpense e) {
        String name = categoryNameMap(e.getUserId()).getOrDefault(e.getCategoryId(), "已删分类");
        return new ClinicExpenseVO(e.getId(), e.getCategoryId(), name,
                e.getVisitId(), e.getExpenseDate(), e.getAmount(), e.getNote());
    }

    /** 落库前校验与赋值（分类存在性 + 金额合法性）。 */
    private void apply(Long userId, ClinicExpense expense, ExpenseSaveRequest req) {
        ClinicExpenseCategory category = categoryMapper.selectById(req.categoryId());
        if (category == null
                || (category.getUserId() != 0L && !userId.equals(category.getUserId()))) {
            throw new BusinessException(ErrorCode.CLINIC_CATEGORY_NOT_FOUND);
        }
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "金额需大于 0");
        }
        expense.setUserId(userId);
        expense.setCategoryId(req.categoryId());
        expense.setVisitId(req.visitId());
        expense.setExpenseDate(req.expenseDate());
        expense.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setNote(req.note());
    }

    /** 归属校验：不存在或非本人抛 NOT_FOUND。 */
    private ClinicExpense requireOwned(Long userId, Long id) {
        ClinicExpense expense = expenseMapper.selectById(id);
        if (expense == null || expense.getUserId() == null || !userId.equals(expense.getUserId())) {
            throw new BusinessException(ErrorCode.CLINIC_EXPENSE_NOT_FOUND);
        }
        return expense;
    }

    /** 全部分类（内置 + 本人）ID → 名称映射。 */
    private Map<Long, String> categoryNameMap(Long userId) {
        List<ClinicExpenseCategory> rows = categoryMapper.selectList(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .and(w -> w.eq(ClinicExpenseCategory::getUserId, 0L)
                        .or().eq(ClinicExpenseCategory::getUserId, userId)));
        Map<Long, String> map = new LinkedHashMap<>();
        for (ClinicExpenseCategory c : rows) {
            map.putIfAbsent(c.getId(), c.getName());
        }
        return map;
    }
}
