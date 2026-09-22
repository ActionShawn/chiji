package com.chiji.module.clinic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.ClinicExpenseCategory;
import com.chiji.module.clinic.dto.CategoryCreateRequest;
import com.chiji.module.clinic.dto.CategorySortRequest;
import com.chiji.module.clinic.mapper.ClinicExpenseCategoryMapper;
import com.chiji.module.clinic.service.ClinicCategoryService;
import com.chiji.module.clinic.vo.ClinicCategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 花费分类服务实现。
 * <p>
 * 内置分类（user_id=0）随 migration 预置，只在读取侧合并；名称唯一性含内置五类
 * （用户不能新建与内置同名的分类）。唯一键 {@code uk(user_id, name)} 不含 deleted，
 * 逻辑删行仍占键位，因此同名新建命中已删行时走「复活」而非新插入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicCategoryServiceImpl implements ClinicCategoryService {

    /** 用户自定义分类排序起始权重（内置五类固定 1~5，自定义从 100 起） */
    private static final int USER_SORT_BASE = 100;
    /** 用户自定义分类排序步长 */
    private static final int USER_SORT_STEP = 10;

    private final ClinicExpenseCategoryMapper categoryMapper;

    @Override
    public List<ClinicCategoryVO> list(Long userId) {
        // @TableLogic 自动追加 deleted=0；内置排前（user_id 升序）+ sort_order 升序
        List<ClinicExpenseCategory> rows = categoryMapper.selectList(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .and(w -> w.eq(ClinicExpenseCategory::getUserId, 0L)
                        .or().eq(ClinicExpenseCategory::getUserId, userId))
                .orderByAsc(ClinicExpenseCategory::getUserId)
                .orderByAsc(ClinicExpenseCategory::getSortOrder));
        return rows.stream()
                .map(c -> new ClinicCategoryVO(
                        c.getId(), c.getName(), c.getUserId() == 0L, c.getSortOrder()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicCategoryVO create(Long userId, CategoryCreateRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "分类名不能为空");
        }
        // 同用户下唯一（含逻辑删行）：命中已删行则复活，避免唯一键冲突
        ClinicExpenseCategory existed = categoryMapper.selectAnyByName(userId, name);
        if (existed != null) {
            if (existed.getDeleted() == null || existed.getDeleted() == 0) {
                throw new BusinessException(ErrorCode.CLINIC_CATEGORY_NAME_CONFLICT);
            }
            int order = nextSortOrder(userId);
            categoryMapper.revive(existed.getId(), order);
            log.info("复活花费分类, userId={}, id={}, name={}", userId, existed.getId(), name);
            return new ClinicCategoryVO(existed.getId(), existed.getName(), false, order);
        }
        // 内置五类同名拦截（唯一键跨 user_id 不拦：内置 user_id=0 与用户行不冲突，需显式校验）
        ClinicExpenseCategory builtin = categoryMapper.selectOne(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .eq(ClinicExpenseCategory::getUserId, 0L)
                .eq(ClinicExpenseCategory::getName, name));
        if (builtin != null) {
            throw new BusinessException(ErrorCode.CLINIC_CATEGORY_NAME_CONFLICT);
        }
        ClinicExpenseCategory category = new ClinicExpenseCategory();
        category.setUserId(userId);
        category.setName(name);
        category.setSortOrder(nextSortOrder(userId));
        categoryMapper.insert(category);
        log.info("新增花费分类, userId={}, id={}, name={}", userId, category.getId(), name);
        return new ClinicCategoryVO(category.getId(), category.getName(), false, category.getSortOrder());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        ClinicExpenseCategory category = categoryMapper.selectById(id);
        if (category == null || category.getUserId() == null || !userId.equals(category.getUserId())) {
            throw new BusinessException(ErrorCode.CLINIC_CATEGORY_NOT_FOUND);
        }
        if (category.getUserId() == 0L) {
            throw new BusinessException(ErrorCode.CLINIC_BUILTIN_CATEGORY_IMMUTABLE);
        }
        categoryMapper.deleteById(id);
        log.info("删除花费分类, userId={}, id={}", userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(Long userId, CategorySortRequest req) {
        List<Long> ids = req.ids() == null ? List.of() : req.ids();
        Set<Long> seen = new HashSet<>();
        int order = USER_SORT_BASE;
        for (Long id : ids) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            ClinicExpenseCategory category = categoryMapper.selectById(id);
            if (category == null || category.getUserId() == null || !userId.equals(category.getUserId())) {
                // 内置分类或他人分类跳过，不打断整体排序
                continue;
            }
            category.setSortOrder(order);
            categoryMapper.updateById(category);
            order += USER_SORT_STEP;
        }
    }

    /** 用户自定义分类当前最大排序权重 + 步长；无分类时返回起始权重。 */
    private int nextSortOrder(Long userId) {
        List<ClinicExpenseCategory> mine = categoryMapper.selectList(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .eq(ClinicExpenseCategory::getUserId, userId));
        return mine.stream()
                .map(ClinicExpenseCategory::getSortOrder)
                .filter(o -> o != null && o >= USER_SORT_BASE)
                .max(Comparator.naturalOrder())
                .map(m -> m + USER_SORT_STEP)
                .orElse(USER_SORT_BASE);
    }
}
