package com.chiji.module.clinic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.ClinicExpense;
import com.chiji.entity.ClinicExpenseCategory;
import com.chiji.entity.ClinicVisit;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.TimelineRecord;
import com.chiji.enums.ClinicVisitStatusEnum;
import com.chiji.enums.RecordBadgeTypeEnum;
import com.chiji.enums.RecordKindEnum;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.entity.User;
import com.chiji.module.clinic.dto.VisitCompleteRequest;
import com.chiji.module.clinic.dto.VisitSaveRequest;
import com.chiji.module.clinic.mapper.ClinicExpenseCategoryMapper;
import com.chiji.module.clinic.mapper.ClinicExpenseMapper;
import com.chiji.module.clinic.mapper.ClinicVisitMapper;
import com.chiji.module.clinic.service.ClinicVisitService;
import com.chiji.module.clinic.service.strategy.BookingRemindStrategy;
import com.chiji.module.clinic.vo.ClinicDayVO;
import com.chiji.module.clinic.vo.ClinicExpenseVO;
import com.chiji.module.clinic.vo.ClinicMonthVO;
import com.chiji.module.clinic.vo.ClinicVisitVO;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 复诊档案服务实现。
 * <p>
 * 完成动作三联动（同一事务）：状态迁移 PLANNED→DONE、投影时光轴 FOLLOW_UP 记录
 * （stageId 非空才建，照片挂 record_media）、nextVisitDate 非空自动生成下一条 PLANNED；
 * 另可一键带出一笔复诊费流水（默认内置「复诊费」分类）。
 * 删除 DONE 记录联动逻辑删投影时光轴记录；时光轴侧删除不回写档案（单向主从）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicVisitServiceImpl implements ClinicVisitService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    /** 默认就诊内容文案（用户未填写时） */
    private static final String DEFAULT_CONTENT = "完成复诊";
    /** 一键带出复诊费的内置分类名 */
    private static final String BUILTIN_EXPENSE_NAME = "复诊费";
    /** 单次补录媒体上限（对齐时光轴记录约束） */
    private static final int MEDIA_MAX = 9;

    private final ClinicVisitMapper visitMapper;
    private final ClinicExpenseMapper expenseMapper;
    private final ClinicExpenseCategoryMapper categoryMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final RecordMediaMapper recordMediaMapper;
    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final UserMapper userMapper;
    private final List<BookingRemindStrategy> bookingStrategies;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicVisitVO create(Long userId, VisitSaveRequest req) {
        if (req.visitDate() == null) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "复诊日期不能为空");
        }
        if (req.remindOffsetDays() != null && (req.remindOffsetDays() < 0 || req.remindOffsetDays() > 3)) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "提醒提前天数不合法");
        }
        ClinicVisit visit = new ClinicVisit();
        visit.setUserId(userId);
        visit.setVisitDate(req.visitDate());
        visit.setStatus(ClinicVisitStatusEnum.PLANNED.name());
        visit.setClinicName(trimToNull(req.clinicName()));
        visit.setDoctorName(trimToNull(req.doctorName()));
        visit.setRemindOffsetDays(req.remindOffsetDays());
        applyStageSnapshot(userId, visit);
        visitMapper.insert(visit);
        log.info("创建复诊日程, userId={}, id={}, date={}", userId, visit.getId(), req.visitDate());
        return toVO(visit, Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicVisitVO update(Long userId, Long id, VisitSaveRequest req) {
        ClinicVisit visit = requireOwned(userId, id);
        if (!ClinicVisitStatusEnum.PLANNED.name().equals(visit.getStatus())) {
            // 已完成记录不允许改期（日期是提醒与统计的锚点），内容补录走 complete 链路
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "已完成记录不可编辑日程");
        }
        if (req.visitDate() == null) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "复诊日期不能为空");
        }
        visit.setVisitDate(req.visitDate());
        visit.setClinicName(trimToNull(req.clinicName()));
        visit.setDoctorName(trimToNull(req.doctorName()));
        visit.setRemindOffsetDays(req.remindOffsetDays());
        visitMapper.updateById(visit);
        return toVO(visit, Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        ClinicVisit visit = requireOwned(userId, id);
        // DONE 记录联动逻辑删投影时光轴记录（媒体随记录在时光轴侧自然失效，不回写档案）
        if (visit.getTimelineRecordId() != null) {
            timelineRecordMapper.deleteById(visit.getTimelineRecordId());
        }
        visitMapper.deleteById(visit.getId());
        log.info("删除复诊记录, userId={}, id={}, timelineRecordId={}",
                userId, id, visit.getTimelineRecordId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClinicVisitVO complete(Long userId, Long id, VisitCompleteRequest req) {
        ClinicVisit visit = requireOwned(userId, id);
        if (!ClinicVisitStatusEnum.PLANNED.name().equals(visit.getStatus())) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "该记录已完成");
        }
        String content = trimToNull(req.content());
        if (content == null) {
            content = DEFAULT_CONTENT;
        }

        // 1) 状态迁移 + 内容补录
        visit.setStatus(ClinicVisitStatusEnum.DONE.name());
        visit.setContent(content);

        // 2) 投影时光轴记录（无阶段快照的用户——如应急就诊——跳过投影，档案记录独立成立）
        if (visit.getStageId() != null) {
            TimelineRecord record = new TimelineRecord();
            record.setUserId(userId);
            record.setStageId(visit.getStageId());
            record.setAlignerId(visit.getAlignerId());
            record.setRecordDate(visit.getVisitDate());
            record.setText(content);
            record.setBadgeType(RecordBadgeTypeEnum.FOLLOW_UP.getCode());
            List<VisitCompleteRequest.MediaItem> medias = req.mediaList() == null
                    ? Collections.emptyList() : req.mediaList();
            if (medias.size() > MEDIA_MAX) {
                throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "媒体数量超出上限");
            }
            record.setMediaKind(computeMediaKind(medias));
            timelineRecordMapper.insert(record);
            visit.setTimelineRecordId(record.getId());

            // 3) 照片挂 record_media（挂在投影时光轴记录上）
            int sort = 1;
            for (VisitCompleteRequest.MediaItem item : medias) {
                if (item == null || trimToNull(item.url()) == null) {
                    continue;
                }
                RecordMedia media = new RecordMedia();
                media.setRecordId(record.getId());
                media.setUrl(item.url().trim());
                media.setType(resolveMediaType(item.type()));
                media.setSortOrder(sort++);
                recordMediaMapper.insert(media);
            }
        }

        visitMapper.updateById(visit);

        // 4) 一键带出复诊费流水（金额 > 0 才生成；分类缺省内置「复诊费」）
        if (req.expenseAmount() != null && req.expenseAmount().signum() > 0) {
            ClinicExpense expense = new ClinicExpense();
            expense.setUserId(userId);
            expense.setCategoryId(resolveExpenseCategoryId(userId, req.expenseCategoryId()));
            expense.setVisitId(visit.getId());
            expense.setExpenseDate(visit.getVisitDate());
            expense.setAmount(req.expenseAmount().setScale(2, RoundingMode.HALF_UP));
            expense.setNote(trimToNull(req.expenseNote()));
            expenseMapper.insert(expense);
        }

        // 5) next_visit_date 非空 → 自动生成下一条 PLANNED（预填诊所/医生，继承提醒偏好与阶段快照）
        if (req.nextVisitDate() != null) {
            ClinicVisit next = new ClinicVisit();
            next.setUserId(userId);
            next.setVisitDate(req.nextVisitDate());
            next.setStatus(ClinicVisitStatusEnum.PLANNED.name());
            next.setClinicName(visit.getClinicName());
            next.setDoctorName(visit.getDoctorName());
            next.setRemindOffsetDays(visit.getRemindOffsetDays());
            next.setStageId(visit.getStageId());
            next.setAlignerId(visit.getAlignerId());
            visitMapper.insert(next);
            log.info("自动生成下次复诊日程, userId={}, id={}, date={}", userId, next.getId(), next.getVisitDate());
        }

        log.info("就诊完成, userId={}, id={}, expense={}, nextVisit={}",
                userId, id, req.expenseAmount(), req.nextVisitDate());
        return toVO(visit, req.mediaList() == null ? Collections.emptyList()
                : req.mediaList().stream()
                        .filter(m -> m != null && trimToNull(m.url()) != null)
                        .map(m -> new ClinicVisitVO.MediaVO(m.url().trim(), resolveMediaType(m.type())))
                        .toList());
    }

    @Override
    public ClinicMonthVO monthView(Long userId, String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank()
                    ? YearMonth.from(WearTimes.today())
                    : YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "月份格式应为 yyyy-MM");
        }
        List<ClinicVisit> visits = visitMapper.selectList(new LambdaQueryWrapper<ClinicVisit>()
                .eq(ClinicVisit::getUserId, userId)
                .ge(ClinicVisit::getVisitDate, ym.atDay(1))
                .le(ClinicVisit::getVisitDate, ym.atEndOfMonth())
                .orderByAsc(ClinicVisit::getVisitDate)
                .orderByAsc(ClinicVisit::getId));
        // 预约窗口锚点（隐形=最终副计划结束日；无阶段/非最终副为 null，页面退化为纯手动模式）
        LocalDate anchor = bookingStrategies.stream()
                .filter(s -> s.supports(currentTreatmentType(userId)))
                .findFirst()
                .map(s -> s.computeWindowAnchor(userId))
                .orElse(null);
        List<ClinicVisitVO> vos = visits.stream().map(v -> toVO(v, Collections.emptyList())).toList();
        return new ClinicMonthVO(ym.format(MONTH_FMT), vos, anchor, anchor != null);
    }

    @Override
    public ClinicDayVO dayView(Long userId, LocalDate date) {
        if (date == null) {
            throw new BusinessException(ErrorCode.CLINIC_PARAM_INVALID, "日期不能为空");
        }
        List<ClinicVisit> visits = visitMapper.selectList(new LambdaQueryWrapper<ClinicVisit>()
                .eq(ClinicVisit::getUserId, userId)
                .eq(ClinicVisit::getVisitDate, date)
                .orderByAsc(ClinicVisit::getId));
        List<Long> doneIds = visits.stream()
                .filter(v -> ClinicVisitStatusEnum.DONE.name().equals(v.getStatus()))
                .filter(v -> v.getTimelineRecordId() != null)
                .map(ClinicVisit::getTimelineRecordId)
                .toList();
        Map<Long, List<ClinicVisitVO.MediaVO>> mediaMap = doneIds.isEmpty()
                ? Map.of()
                : recordMediaMapper.selectList(new LambdaQueryWrapper<RecordMedia>()
                        .in(RecordMedia::getRecordId, doneIds)
                        .orderByAsc(RecordMedia::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(RecordMedia::getRecordId,
                        Collectors.mapping(m -> new ClinicVisitVO.MediaVO(m.getUrl(), m.getType()),
                                Collectors.toList())));
        List<ClinicVisitVO> visitVos = visits.stream()
                .map(v -> toVO(v, v.getTimelineRecordId() == null
                        ? Collections.emptyList()
                        : mediaMap.getOrDefault(v.getTimelineRecordId(), Collections.emptyList())))
                .toList();
        List<ClinicExpense> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ClinicExpense>()
                .eq(ClinicExpense::getUserId, userId)
                .eq(ClinicExpense::getExpenseDate, date)
                .orderByDesc(ClinicExpense::getId));
        Map<Long, String> names = categoryNameMap(userId);
        List<ClinicExpenseVO> expenseVos = expenses.stream()
                .map(e -> new ClinicExpenseVO(e.getId(), e.getCategoryId(),
                        names.getOrDefault(e.getCategoryId(), "已删分类"),
                        e.getVisitId(), e.getExpenseDate(), e.getAmount(), e.getNote()))
                .toList();
        return new ClinicDayVO(date, visitVos, expenseVos);
    }

    @Override
    public ClinicVisitVO lastVisit(Long userId) {
        ClinicVisit visit = visitMapper.selectOne(new LambdaQueryWrapper<ClinicVisit>()
                .eq(ClinicVisit::getUserId, userId)
                .eq(ClinicVisit::getStatus, ClinicVisitStatusEnum.DONE.name())
                .orderByDesc(ClinicVisit::getVisitDate)
                .orderByDesc(ClinicVisit::getId)
                .last("LIMIT 1"));
        return visit == null ? null : toVO(visit, Collections.emptyList());
    }

    // ───────────────────────── 私有辅助 ─────────────────────────

    /** 创建时快照 ACTIVE 阶段与当前副（可空；仅展示用，不随阶段变化回写）。 */
    private void applyStageSnapshot(Long userId, ClinicVisit visit) {
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .eq(Stage::getStatus, StageStatusEnum.ACTIVE.name())
                .orderByDesc(Stage::getId)
                .last("LIMIT 1"));
        if (stage == null) {
            return;
        }
        visit.setStageId(stage.getId());
        Aligner active = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stage.getId())
                .eq(Aligner::getState, AlignerStateEnum.ACTIVE.name())
                .orderByDesc(Aligner::getId)
                .last("LIMIT 1"));
        if (active != null) {
            visit.setAlignerId(active.getId());
        }
    }

    /** 一键带出复诊费的分类解析：指定 ID 须合法，否则取内置「复诊费」；无内置时取「其他」。 */
    private Long resolveExpenseCategoryId(Long userId, Long requestedId) {
        if (requestedId != null) {
            ClinicExpenseCategory c = categoryMapper.selectById(requestedId);
            if (c != null && (c.getUserId() == 0L || userId.equals(c.getUserId()))) {
                return requestedId;
            }
        }
        ClinicExpenseCategory builtin = categoryMapper.selectOne(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .eq(ClinicExpenseCategory::getUserId, 0L)
                .eq(ClinicExpenseCategory::getName, BUILTIN_EXPENSE_NAME));
        if (builtin != null) {
            return builtin.getId();
        }
        ClinicExpenseCategory other = categoryMapper.selectOne(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .eq(ClinicExpenseCategory::getUserId, 0L)
                .eq(ClinicExpenseCategory::getName, "其他"));
        return other == null ? null : other.getId();
    }

    /** 当前用户治疗类型（查不到用户返回 null，策略全部 miss → 无预约窗口）。 */
    private String currentTreatmentType(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getTreatmentType();
    }

    /** 媒体类型汇总（无媒体=TEXT，全图=IMAGE，全视频=VIDEO，混合=MIXED），与时光轴规则一致。 */
    private String computeMediaKind(List<VisitCompleteRequest.MediaItem> medias) {
        if (medias == null || medias.isEmpty()) {
            return RecordKindEnum.TEXT.getCode();
        }
        boolean hasImage = false;
        boolean hasVideo = false;
        for (VisitCompleteRequest.MediaItem item : medias) {
            if ("VIDEO".equals(resolveMediaType(item == null ? null : item.type()))) {
                hasVideo = true;
            } else {
                hasImage = true;
            }
        }
        if (hasImage && hasVideo) {
            return RecordKindEnum.MIXED.getCode();
        }
        return hasVideo ? RecordKindEnum.VIDEO.getCode() : RecordKindEnum.IMAGE.getCode();
    }

    /** 归一媒体类型为 IMAGE / VIDEO（空或未识别按 IMAGE）。 */
    private String resolveMediaType(String type) {
        return "VIDEO".equalsIgnoreCase(trimToNull(type)) ? "VIDEO" : "IMAGE";
    }

    /** 归属校验：不存在或非本人抛 NOT_FOUND。 */
    private ClinicVisit requireOwned(Long userId, Long id) {
        ClinicVisit visit = visitMapper.selectById(id);
        if (visit == null || visit.getUserId() == null || !userId.equals(visit.getUserId())) {
            throw new BusinessException(ErrorCode.CLINIC_VISIT_NOT_FOUND);
        }
        return visit;
    }

    /** 实体 → VO（mediaList 由调用方按需组装）。 */
    private ClinicVisitVO toVO(ClinicVisit v, List<ClinicVisitVO.MediaVO> medias) {
        return new ClinicVisitVO(v.getId(), v.getVisitDate(), v.getStatus(),
                v.getClinicName(), v.getDoctorName(), v.getContent(), v.getNextVisitDate(),
                v.getStageId(), v.getAlignerId(), v.getTimelineRecordId(), v.getRemindOffsetDays(),
                medias);
    }

    /** 全部分类（内置 + 本人）ID → 名称映射。 */
    private Map<Long, String> categoryNameMap(Long userId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<ClinicExpenseCategory>()
                .and(w -> w.eq(ClinicExpenseCategory::getUserId, 0L)
                        .or().eq(ClinicExpenseCategory::getUserId, userId)))
                .stream()
                .collect(Collectors.toMap(ClinicExpenseCategory::getId,
                        ClinicExpenseCategory::getName, (a, b) -> a));
    }

    /** trim 后空串转 null。 */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
