package com.chiji.module.wear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.UserSetting;
import com.chiji.entity.WearDailySummary;
import com.chiji.entity.WearSession;
import com.chiji.entity.WearSessionEditLog;
import com.chiji.enums.StageStatusEnum;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.enums.WearSourceEnum;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.dto.WearSessionEditRequest;
import com.chiji.module.wear.mapper.WearDailySummaryMapper;
import com.chiji.module.wear.mapper.WearSessionEditLogMapper;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.mapper.WearUserSettingMapper;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.service.WearSettleService;
import com.chiji.module.wear.support.TakeoffTimeoutSpec;
import com.chiji.module.wear.support.WearDayMath;
import com.chiji.module.wear.support.WearTimes;
import com.chiji.module.wear.vo.ConsultSummaryVO;
import com.chiji.module.wear.vo.GoalVO;
import com.chiji.module.wear.vo.TodaySessionVO;
import com.chiji.module.wear.vo.TodayWearVO;
import com.chiji.module.wear.vo.WearAlignerSummaryVO;
import com.chiji.module.wear.vo.WearStatsVO;
import com.chiji.module.wear.vo.WearTopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 佩戴时长核心服务实现。见 {@link WearService}。
 * <p>
 * 会话读写均基于 Asia/Shanghai 天然日；今日量实时由会话推导，昨日/更早由结算表物化
 * 后只读派生（连续达标、达标庆祝均为当天首次观测时通过 message 模块归档一次）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WearServiceImpl implements WearService {

    /** 目标允许下限 18.0h（秒） */
    private static final int MIN_GOAL_SEC = (int) (18.0 * 3600);
    /** 目标允许上限 22.0h（秒） */
    private static final int MAX_GOAL_SEC = (int) (22.0 * 3600);
    /** 目标步进 0.5h（秒） */
    private static final int GOAL_STEP_SEC = (int) (0.5 * 3600);
    /** 校准补录可回补的最早自然日偏移（今天-6，含今天共 7 天可补录/修正） */
    private static final long MAKEUP_BACK_DAYS = 6;
    /**
     * 会话重叠查询中「开放段（佩戴中，无结束时间）」使用的伪无穷上限。
     * 不能直接用 {@link LocalDateTime#MAX}（年份超出 MySQL DATETIME(3) 可绑定范围，驱动 setTimestamp 溢出抛 DateTimeException）；
     * 9999-12-31T23:59:59 在 DATETIME 范围内，对本业务即为「无穷早于任意会话起始」。
     */
    private static final LocalDateTime OPEN_END_FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final WearSessionMapper wearSessionMapper;
    private final WearSessionEditLogMapper wearSessionEditLogMapper;
    private final WearDailySummaryMapper wearDailySummaryMapper;
    private final WearUserSettingMapper wearUserSettingMapper;
    private final WearSettleService wearSettleService;
    private final AlignerService alignerService;
    private final ReminderNotifyService reminderNotifyService;
    private final com.chiji.module.delay.service.DelayTaskService delayTaskService;
    private final com.chiji.module.message.service.NotificationSettingService notificationSettingService;

    // ── 工作台 ──────────────────────────────────────────────

    @Override
    public TodayWearVO today(Long userId) {
        return readToday(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO punch(Long userId, String action, String mode) {
        WearSession open = findOpenSession(userId);
        if ("WEAR_ON".equals(action)) {
            if (open == null) {
                WearSession s = newSession(userId, WearSourceEnum.MANUAL, null, WearTimes.now(), null);
                // 归属「当前选择阶段的当前副」：按记录模式取该模式 ACTIVE 阶段里 state=ACTIVE 的副，
                // 不跨模式/跨阶段误配；该模式无 ACTIVE 副时为 null
                com.chiji.entity.Aligner worn = alignerService.findActiveAligner(userId, mode);
                s.setAlignerId(worn == null ? null : worn.getId());
                wearSessionMapper.insert(s);
                log.info("佩戴打卡开, userId={}, sessionId={}, mode={}, alignerId={}", userId, s.getId(), mode, s.getAlignerId());
                // BE-5：戴回取消摘下超时任务（幂等；取消落在本事务内，与投递同事务保证一致性）
                cancelTakeoffTimeout(userId);
            }
            // 已有佩戴中会话则幂等返回（不重复开段）
        } else if ("WEAR_OFF".equals(action)) {
            if (open == null) {
                throw new BusinessException(ErrorCode.WEAR_ACTIVE_SESSION_NOT_FOUND);
            }
            open.setEndedAt(WearTimes.now());
            wearSessionMapper.updateById(open);
            log.info("佩戴打卡关, userId={}, sessionId={}", userId, open.getId());
            // BE-4：摘下超时提醒投递（前置：类型开关开启；开关/方式不满足则不投递，不阻断打卡）
            submitTakeoffTimeout(userId, open.getId());
        } else {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "打卡动作应为 WEAR_ON / WEAR_OFF");
        }
        return readToday(userId);
    }

    // ── 摘下超时提醒（BE-4 投递 / BE-5 取消 / BE-6 围栏支撑） ─────────────

    /**
     * 摘下超时提醒投递（punch WEAR_OFF 事务内调用）。
     * <p>
     * 前置不满足则静默不投递：类型开关（总开关 + 摘下超时提醒类型开关）关闭。开关开启后按
     * {@code remindMode} 以摘下时刻快照一次性计算 executeAt（智能模式用今日目标与今日已佩戴，
     * 已佩戴时长与统计侧共用 {@link #sessionsOverlapping} + {@link WearDayMath#distribute} 同一套
     * 跨零点切分口径）。「摘下即达上限」的 executeAt = now+1s，走调度泵正常路径，
     * 不在本事务内同步下发。
     */
    private void submitTakeoffTimeout(Long userId, Long sessionId) {
        if (!notificationSettingService.isReminderAllowed(userId, WearReminderTypeEnum.TAKEOFF_TIMEOUT)) {
            log.info("摘下超时提醒未开启，跳过投递, userId={}, sessionId={}", userId, sessionId);
            return;
        }
        String remindMode = notificationSettingService.takeoffRemindMode(userId);
        LocalDateTime takeoffAt = WearTimes.now();
        int goalSec = goalSec(userId);
        long wornSec = wornSecToday(userId);
        boolean immediate = TakeoffTimeoutSpec.MODE_SMART.equals(remindMode)
                && TakeoffTimeoutSpec.isSmartImmediate(takeoffAt, goalSec, wornSec);
        LocalDateTime executeAt = TakeoffTimeoutSpec.calcExecuteAt(remindMode, takeoffAt, goalSec, wornSec);
        delayTaskService.submit(TakeoffTimeoutSpec.TASK_TYPE, TakeoffTimeoutSpec.bizKey(sessionId), executeAt,
                "摘下超时提醒", TakeoffTimeoutSpec.buildPayload(userId, sessionId, remindMode,
                        takeoffAt, goalSec, wornSec, immediate));
        log.info("摘下超时提醒已投递, userId={}, sessionId={}, remindMode={}, executeAt={}",
                userId, sessionId, remindMode, executeAt);
    }

    /**
     * 戴回取消摘下超时任务（punch WEAR_ON 事务内调用，幂等）。
     * <p>
     * 业务键按「最近一条已收口会话」定位——即最近一次摘下动作投递的任务；无摘下记录时
     * 取消空键无效但不报错。
     */
    private void cancelTakeoffTimeout(Long userId) {
        Long closedId = latestClosedSessionId(userId);
        if (closedId != null) {
            delayTaskService.cancel(TakeoffTimeoutSpec.bizKey(closedId));
            log.info("戴回取消摘下超时任务, userId={}, sessionId={}", userId, closedId);
        }
    }

    /** 摘下时刻的今日已佩戴时长（秒）：与统计侧共用同一会话查询与跨零点切分口径。 */
    private long wornSecToday(Long userId) {
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        List<WearSession> sessions = sessionsOverlapping(userId, WearTimes.startOf(today), now);
        return Math.max(0, WearDayMath.dayOrZero(WearDayMath.distribute(sessions, today, today, now), today).wear());
    }

    @Override
    public boolean isTakeoffSessionOpen(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }
        WearSession closed = wearSessionMapper.selectById(sessionId);
        if (closed == null || !userId.equals(closed.getUserId()) || closed.getEndedAt() == null) {
            return false;
        }
        // 用户已戴回（存在佩戴中会话）→ 摘下被打断
        Long openCount = wearSessionMapper.selectCount(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .isNull(WearSession::getEndedAt));
        return openCount == null || openCount == 0;
    }

    @Override
    public Long latestClosedSessionId(Long userId) {
        WearSession last = wearSessionMapper.selectOne(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .isNotNull(WearSession::getEndedAt)
                .orderByDesc(WearSession::getEndedAt)
                .last("LIMIT 1"));
        return last == null ? null : last.getId();
    }

    @Override
    public int currentGoalSec(Long userId) {
        return goalSec(userId);
    }

    @Override
    public void cancelLatestTakeoffTimeout(Long userId) {
        cancelTakeoffTimeout(userId);
    }

    @Override
    public ConsultSummaryVO consultSummary(Long userId, String period, Long stageId) {
        String p = period == null ? "" : period.trim().toUpperCase();
        if (!"ALIGNER".equals(p) && !"STAGE".equals(p) && !"LAST30".equals(p)) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "统计周期应为 ALIGNER / STAGE / LAST30");
        }
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        com.chiji.entity.Aligner active = alignerService.findActiveAligner(userId, null);

        // 1. 周期解析：阶段 + 目标副。BE-8 契约：
        //    ALIGNER 有进行中副取当前副，无则回落「本阶段最后一副全程」；阶段不可解析（如阶段已
        //    全部结束且未传 stageId）再回落用户最近一副，避免把有完整历史的用户误判为「从未创建阶段」；
        //    STAGE 按 resolveStage 既有语义解析阶段（stageId 空回退 ACTIVE 阶段）；
        //    LAST30 副取「当前副或最近一副」（周期与阶段无关，stage 字段恒 null）。
        com.chiji.entity.Stage stage = null;
        com.chiji.entity.Aligner target = null;
        if ("STAGE".equals(p)) {
            stage = alignerService.resolveStage(userId, stageId);
            if (stage != null) {
                boolean activeInStage = active != null && stage.getId().equals(active.getStageId());
                target = activeInStage ? active : alignerService.findLastAlignerOfStage(stage.getId());
            }
        } else if ("ALIGNER".equals(p)) {
            target = active;
            if (target != null) {
                stage = target.getStageId() != null
                        ? alignerService.resolveStage(userId, target.getStageId()) : null;
            }
            if (target == null) {
                stage = alignerService.resolveStage(userId, stageId);
                target = stage != null ? alignerService.findLastAlignerOfStage(stage.getId()) : null;
                if (target == null && stage == null) {
                    target = alignerService.findLatestAlignerOfUser(userId);
                    stage = target != null && target.getStageId() != null
                            ? alignerService.resolveStage(userId, target.getStageId()) : null;
                }
            }
        } else {
            target = active != null ? active : alignerService.findLatestAlignerOfUser(userId);
        }

        if (stage == null && target == null) {
            // 从未创建阶段（或 stageId 非本人且无任何副）：全空 NO_STAGE
            return new ConsultSummaryVO(p, ConsultSummaryVO.STATE_NO_STAGE, true,
                    null, null, 0, null, 0, 0, 0, List.of(), false, null);
        }

        // 2. 周期起止：起点 = 副/阶段开始日（缺失回退今天，未来排期截断到今天）；
        //    终点真实判定：进行中 / 含今日为 null；已结束阶段收口至阶段结束日
        //    （阶段无独立结束日列，由阶段内最后一副结束日派生，不混入后续阶段会话）
        LocalDate from;
        if ("ALIGNER".equals(p)) {
            from = target != null && target.getStartDate() != null ? target.getStartDate() : today;
        } else if ("STAGE".equals(p)) {
            from = stage.getStartDate() != null ? stage.getStartDate()
                    : (target != null && target.getStartDate() != null ? target.getStartDate() : today);
        } else {
            from = today.minusDays(29);
        }
        if (from.isAfter(today)) {
            from = today;
        }
        LocalDate rawEnd = null;
        if ("STAGE".equals(p)) {
            if (!StageStatusEnum.ACTIVE.getCode().equals(stage.getStatus()) && target != null) {
                rawEnd = target.getEndDate();
            }
        } else if ("ALIGNER".equals(p) && target != null) {
            rawEnd = target.getEndDate();
        }
        boolean inProgress = rawEnd == null || !rawEnd.isBefore(today);
        // 统计窗口收口：终点越过今天按今天截断（未来排期日不产生空评判日）
        LocalDate statEnd = rawEnd == null || rawEnd.isAfter(today) ? today : rawEnd;
        if (statEnd.isBefore(from)) {
            statEnd = from;
        }
        LocalDate stageEnd = stage != null && !StageStatusEnum.ACTIVE.getCode().equals(stage.getStatus())
                ? rawEnd : null;

        // 3. 会话查询 + 切分：与今日工作台/统计侧共用同一套跨零点口径（sessionsOverlapping + distribute）
        List<WearSession> sessions = sessionsOverlapping(userId, WearTimes.startOf(from), now);
        if (sessions.isEmpty()) {
            // 有阶段/副但所选周期内无任何打卡：态C 结构化空标记（阶段与副进度照常返回）
            return stageNoRecord(p, stage, stageEnd, target, inProgress);
        }

        // 4. 逐日归并：记录天数直出 + 未达标判定 + 汇总
        int goal = goalSec(userId);
        Map<LocalDate, WearDayMath.DaySecs> daily = WearDayMath.distribute(sessions, from, statEnd, now);
        LocalDate goalSince = goalSinceDate(userId);
        long total = 0;
        int recordDays = 0;
        int underCount = 0;
        List<ConsultSummaryVO.UnderDay> underDays = new ArrayList<>();
        for (Map.Entry<LocalDate, WearDayMath.DaySecs> e : daily.entrySet()) {
            long wear = Math.max(0, e.getValue().wear());
            total += wear;
            if (wear > 0) {
                recordDays++;
            }
            // 无基准不评判：目标最近设置/修改日之前的日子不计入未达标（中途改目标不追溯）
            if (e.getKey().isBefore(goalSince)) {
                continue;
            }
            if (wear < goal) {
                underCount++;
                underDays.add(new ConsultSummaryVO.UnderDay(e.getKey().toString(), wear, goal));
            }
        }
        if (recordDays == 0) {
            // 会话全部落在统计窗口之外（如已结束阶段之后开启的新阶段会话）：态C
            return stageNoRecord(p, stage, stageEnd, target, inProgress);
        }
        long avg = total / recordDays;
        return new ConsultSummaryVO(p, ConsultSummaryVO.STATE_DATA, false,
                from.toString(), inProgress ? null : rawEnd.toString(), recordDays,
                stageInfoOf(p, stage, stageEnd), total, avg, underCount, underDays,
                inProgress, progressOf(target));
    }

    /** 态C（有阶段但所选周期内零打卡）：日期与统计置空，阶段与副进度照常返回。 */
    private ConsultSummaryVO stageNoRecord(String p, com.chiji.entity.Stage stage, LocalDate stageEnd,
                                           com.chiji.entity.Aligner target, boolean inProgress) {
        return new ConsultSummaryVO(p, ConsultSummaryVO.STATE_STAGE_NO_RECORD, true,
                null, null, 0, stageInfoOf(p, stage, stageEnd), 0, 0, 0, List.of(),
                inProgress, progressOf(target));
    }

    /** 阶段信息（LAST30 档周期与阶段无关，恒 null）。 */
    private ConsultSummaryVO.StageInfo stageInfoOf(String p, com.chiji.entity.Stage stage, LocalDate endDate) {
        if (stage == null || "LAST30".equals(p)) {
            return null;
        }
        return new ConsultSummaryVO.StageInfo(stage.getId(), stage.getName(),
                stage.getStartDate() == null ? null : stage.getStartDate().toString(),
                endDate == null ? null : endDate.toString());
    }

    /**
     * 目标副进度（ALIGNER=当前副/本阶段最后一副；STAGE=阶段内当前副或最后一副；LAST30=当前副或最近一副）。
     * <p>
     * wornDays 保持既有口径：自本副开始日起至今自然日数含今日（与阶段进度既有展示一致，
     * 已收口副不按结束日截断）；与顶层 recordDays（周期内有佩戴记录的天数）不同名不混用。
     */
    private ConsultSummaryVO.AlignerProgress progressOf(com.chiji.entity.Aligner target) {
        if (target == null) {
            return null;
        }
        LocalDate today = WearTimes.now().toLocalDate();
        int wornDays = target.getStartDate() != null
                ? (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(target.getStartDate(), today) + 1)
                : (target.getCurrentDay() != null ? target.getCurrentDay() : 1);
        return new ConsultSummaryVO.AlignerProgress(
                target.getId(), target.getStageId(),
                target.getNum() == null ? 0 : target.getNum(),
                alignerService.countAligners(target.getStageId()),
                wornDays,
                alignerService.alignerPlannedDays(target.getId()),
                target.getStartDate() == null ? null : target.getStartDate().toString(),
                target.getEndDate() == null ? null : target.getEndDate().toString());
    }

    /**
     * 目标基准日（未达标评判起点）。PRD 2026-10-08 边界 2 拍板：优先取 {@code goal_updated_at}
     * （目标最近设置/修改日，按目标生效日精确评判）；存量行/未记录时该列为 NULL，
     * <b>宽松回退 {@code updated_at} 近似</b>（旧口径：整行最近更新日，可能因其他设置项保存而
     * 偏晚 → 基准日偏晚 → 少评判，宽松方向可接受，见 PRD 边界 2）；均无记录按 MIN（全部纳入评判）。
     */
    private LocalDate goalSinceDate(Long userId) {
        UserSetting s = wearUserSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId));
        if (s == null) {
            return LocalDate.MIN;
        }
        if (s.getGoalUpdatedAt() != null) {
            return s.getGoalUpdatedAt().toLocalDate();
        }
        return s.getUpdatedAt() == null ? LocalDate.MIN : s.getUpdatedAt().toLocalDate();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO cancelActive(Long userId) {
        WearSession open = findOpenSession(userId);
        if (open == null) {
            throw new BusinessException(ErrorCode.WEAR_ACTIVE_SESSION_NOT_FOUND);
        }
        wearSessionMapper.deleteById(open.getId());
        log.info("撤销佩戴中会话, userId={}, sessionId={}", userId, open.getId());
        return readToday(userId);
    }

    // ── 补录 / 补记 ─────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO makeup(Long userId, WearMakeupRequest req) {
        if (req == null || req.date() == null || req.segments() == null || req.segments().isEmpty()) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "补录日期与时间段不能为空");
        }
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        LocalDate date = parseDate(req.date());
        // 只允许补录 [今天-6, 今天]（最近 7 天含今天）
        if (date.isBefore(today.minusDays(MAKEUP_BACK_DAYS)) || date.isAfter(today)) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_OUT_OF_RANGE);
        }

        // 解析并按开始时刻排序，内部先做重叠校验
        List<WearMakeupRequest.MakeupSegment> segs = new ArrayList<>(req.segments());
        segs.sort(Comparator.comparing(WearMakeupRequest.MakeupSegment::start));
        LocalDateTime prevEnd = null;
        long dayTotal = 0;
        for (WearMakeupRequest.MakeupSegment seg : segs) {
            LocalTime st = parseTime(seg.start());
            LocalTime et = parseTime(seg.end());
            if (st == null || et == null || !et.isAfter(st)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "时间段格式应为 HH:mm 且结束晚于开始");
            }
            LocalDateTime start = date.atTime(st);
            LocalDateTime end = date.atTime(et);
            // 今天补录：结束不得晚于当前时刻
            if (date.equals(today) && end.isAfter(now)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "结束时间不能晚于当前时间");
            }
            if (prevEnd != null && start.isBefore(prevEnd)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_OVERLAP, "补录时间段不能相互重叠");
            }
            prevEnd = end;
            dayTotal += Duration.between(start, end).getSeconds();
        }
        if (dayTotal > 24L * 3600) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "单日补录时长不能超过 24 小时");
        }

        // 归属副：补录日期当天当时佩戴的副
        com.chiji.entity.Aligner worn = alignerService.findWornAligner(userId, date, req.mode());
        for (WearMakeupRequest.MakeupSegment seg : segs) {
            LocalDateTime start = date.atTime(parseTime(seg.start()));
            LocalDateTime end = date.atTime(parseTime(seg.end()));
            if (hasOverlap(userId, start, end)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_OVERLAP);
            }
            WearSession s = newSession(userId, WearSourceEnum.MAKEUP, date, start, end);
            s.setAlignerId(worn == null ? null : worn.getId());
            wearSessionMapper.insert(s);
            log.info("校准补录入库, userId={}, sessionId={}, date={}, {}-{}", userId, s.getId(), date, seg.start(), seg.end());
        }
        // 补录影响的是历史日，重算该日结算（覆盖）
        wearSettleService.settleDay(userId, date);
        return readToday(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO morningBackfill(Long userId, WearMorningBackfillRequest req) {
        if (req == null || req.date() == null || req.startTime() == null) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "补记日期与戴上时间不能为空");
        }
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        LocalDate date = parseDate(req.date());
        // 晨起补记针对「昨晚」：date 必须 = 今天-1
        if (!date.equals(today.minusDays(1))) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "只能补记昨晚的佩戴（date = 昨天）");
        }
        LocalTime st = parseTime(req.startTime());
        if (st == null) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "戴上时间格式应为 HH:mm");
        }
        LocalDateTime start = date.atTime(st);
        boolean stillWearing = Boolean.TRUE.equals(req.stillWearing());
        LocalDateTime end = null;
        if (!stillWearing) {
            LocalTime et = parseTime(req.endTime());
            if (et == null) {
                throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "已摘下时需填写摘下时刻 HH:mm");
            }
            end = today.atTime(et);
            if (!end.isAfter(start)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "摘下时刻需晚于戴上时刻");
            }
            if (end.isAfter(now)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "摘下时刻不能晚于当前时间");
            }
        } else if (findOpenSession(userId) != null) {
            // 现在还戴着：不允许同时存在另一段佩戴中会话
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_OVERLAP, "当前已有一段佩戴中会话");
        }
        if (hasOverlap(userId, start, end)) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_OVERLAP);
        }

        com.chiji.entity.Aligner worn = alignerService.findWornAligner(userId, date, req.mode());
        WearSession s = newSession(userId, WearSourceEnum.MANUAL, null, start, end);
        s.setAlignerId(worn == null ? null : worn.getId());
        wearSessionMapper.insert(s);
        log.info("晨起补记入库, userId={}, sessionId={}, start={}, end={}", userId, s.getId(), start, end);
        // 补记真实影响昨晚，重算昨晚结算
        wearSettleService.settleDay(userId, date);
        return readToday(userId);
    }

    // ── 目标 ────────────────────────────────────────────────

    @Override
    public GoalVO getGoal(Long userId) {
        int goal = goalSec(userId);
        return toGoalVO(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalVO updateGoal(Long userId, Double goalHours) {
        if (goalHours == null || goalHours.isNaN() || goalHours.isInfinite()) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "目标缺失");
        }
        int sec = (int) Math.round(goalHours * 3600);
        if (sec < MIN_GOAL_SEC || sec > MAX_GOAL_SEC) {
            throw new BusinessException(ErrorCode.WEAR_GOAL_OUT_OF_RANGE);
        }
        if (sec % GOAL_STEP_SEC != 0) {
            throw new BusinessException(ErrorCode.WEAR_GOAL_OUT_OF_RANGE, "目标需按 0.5 小时步进");
        }
        UserSetting setting = ensureSettingRow(userId);
        // PRD 2026-10-08 边界 2 拍板：goal_updated_at 记录目标设置/修改时刻，作为复诊小结
        // 未达标评判基准日的精确依据；仅目标值变化时刷新（值未变不重置基准），其余设置项保存不动它
        boolean goalChanged = setting.getGoalSec() == null || !setting.getGoalSec().equals(sec);
        setting.setGoalSec(sec);
        if (goalChanged) {
            setting.setGoalUpdatedAt(WearTimes.now());
        }
        wearUserSettingMapper.updateById(setting);
        log.info("佩戴目标更新, userId={}, goalHours={}", userId, goalHours);
        return toGoalVO(sec);
    }

    // ── 统计 / 汇总 ─────────────────────────────────────────

    @Override
    public WearStatsVO stats(Long userId, String range) {
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        // 三种口径：LAST_7（周档）/ CURRENT_MONTH（月档，自然月 1 号→今天）/ LAST_30（兼容旧调用）
        String normalized;
        LocalDate from;
        if ("CURRENT_MONTH".equalsIgnoreCase(range)) {
            normalized = "CURRENT_MONTH";
            from = today.withDayOfMonth(1);
        } else if ("LAST_30".equalsIgnoreCase(range)) {
            normalized = "LAST_30";
            from = today.minusDays(29);
        } else if ("LAST_7".equalsIgnoreCase(range) || range == null || range.isBlank()) {
            normalized = "LAST_7";
            from = today.minusDays(6);
        } else {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "统计范围应为 LAST_7 / CURRENT_MONTH / LAST_30");
        }
        List<WearSession> sessions = sessionsOverlapping(userId, WearTimes.startOf(from), now);
        Map<LocalDate, WearDayMath.DaySecs> dayMap = WearDayMath.distribute(sessions, from, today, now);

        int goal = goalSec(userId);
        int full = 0;
        int partial = 0;
        int none = 0;
        long sum = 0;
        int hasWear = 0;
        List<String> dates = new ArrayList<>();
        List<Long> secs = new ArrayList<>();
        LocalDate bestDate = null;
        long bestSec = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            WearDayMath.DaySecs ds = WearDayMath.dayOrZero(dayMap, d);
            int s = (int) Math.min(Integer.MAX_VALUE, ds.wear());
            dates.add(d.toString());
            secs.add((long) s);
            sum += s;
            if (s > 0) {
                hasWear++;
            }
            if (s > bestSec) {
                bestSec = s;
                bestDate = d;
            }
            switch (WearSettleServiceImpl.levelOf(s, goal)) {
                case FULL -> full++;
                case PARTIAL -> partial++;
                default -> none++;
            }
        }
        return new WearStatsVO(
                normalized,
                dates,
                secs,
                goal,
                full,
                partial,
                none,
                hasWear == 0 ? 0 : sum / hasWear,
                bestDate == null ? null : bestDate.toString(),
                bestSec == 0 ? null : bestSec
        );
    }

    @Override
    public WearAlignerSummaryVO alignerSummary(Long userId, Long alignerId) {
        // 按会话归属统计：仅累计 aligner_id 归属该副的会话，跨日会话按自然日切分
        List<WearSession> sessions = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .eq(WearSession::getAlignerId, alignerId)
                .isNotNull(WearSession::getStartedAt)
                .orderByAsc(WearSession::getStartedAt));
        int goal = goalSec(userId);
        long total = 0;
        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (WearSession s : sessions) {
            LocalDate d = s.getStartedAt().toLocalDate();
            LocalDate endDay = s.getEndedAt() == null ? WearTimes.today() : s.getEndedAt().toLocalDate();
            for (LocalDate day = d; !day.isAfter(endDay); day = day.plusDays(1)) {
                LocalDateTime dayStart = WearTimes.startOf(day);
                LocalDateTime dayEnd = WearTimes.endOf(day);
                LocalDateTime lo = s.getStartedAt().isAfter(dayStart) ? s.getStartedAt() : dayStart;
                LocalDateTime hi = s.getEndedAt() == null ? WearTimes.now() : s.getEndedAt();
                if (hi.isAfter(dayEnd)) {
                    hi = dayEnd;
                }
                if (hi.isAfter(lo)) {
                    long sec = Duration.between(lo, hi).getSeconds();
                    total += sec;
                    perDay.merge(day, sec, Long::sum);
                }
            }
        }
        List<WearAlignerSummaryVO.DaySec> dayList = new ArrayList<>();
        int fullDays = 0;
        for (Map.Entry<LocalDate, Long> e : perDay.entrySet()) {
            dayList.add(new WearAlignerSummaryVO.DaySec(e.getKey().toString(), e.getValue()));
            if (e.getValue() >= goal) {
                fullDays++;
            }
        }
        return new WearAlignerSummaryVO(alignerId, total, perDay.size(), fullDays, dayList);
    }

    // ── 首页联动 ────────────────────────────────────────────

    @Override
    public WearTopVO wearTop(Long userId) {
        // 首页顶部概览无模式入参，保持不区分模式取任一 ACTIVE 副
        com.chiji.entity.Aligner active = alignerService.findActiveAligner(userId, null);
        if (active == null) {
            return null;
        }
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        List<WearSession> sessions = sessionsOverlapping(userId, WearTimes.startOf(today), now);
        Map<LocalDate, WearDayMath.DaySecs> dayMap = WearDayMath.distribute(sessions, today, today, now);
        WearDayMath.DaySecs ds = WearDayMath.dayOrZero(dayMap, today);
        int goal = goalSec(userId);
        boolean wearing = false;
        Long since = null;
        for (WearSession s : sessions) {
            if (s.getEndedAt() == null) {
                wearing = true;
                since = WearTimes.toEpochMillis(s.getStartedAt());
                break;
            }
        }
        long wear = ds.wear();
        return new WearTopVO(
                active.getId(),
                active.getState() == null ? null : active.getState().toLowerCase(),
                wearing,
                since,
                goal,
                wear,
                WearDayMath.tenthHoursLabel(wear),
                WearDayMath.percent(wear, goal),
                wear >= goal
        );
    }

    // ── 会话增删改（时长校准） ───────────────────────────────

    @Override
    public List<TodaySessionVO> sessions(Long userId, LocalDate date) {
        LocalDateTime now = WearTimes.now();
        LocalDateTime dayStart = WearTimes.startOf(date);
        LocalDateTime dayCap = date.equals(now.toLocalDate()) ? now : WearTimes.endOf(date);
        List<TodaySessionVO> vos = new ArrayList<>();
        for (WearSession s : sessionsOverlapping(userId, dayStart, dayCap)) {
            LocalDateTime[] clip = WearDayMath.clip(s, date, dayCap);
            if (clip == null) {
                continue;
            }
            boolean wearing = s.getEndedAt() == null;
            long dur = wearing
                    ? Duration.between(clip[0], now).getSeconds()
                    : Duration.between(clip[0], clip[1]).getSeconds();
            vos.add(new TodaySessionVO(
                    s.getId(),
                    s.getSource(),
                    s.getMakeupFor() == null ? null : s.getMakeupFor().toString(),
                    WearTimes.toEpochMillis(clip[0]),
                    clip[1] == null ? null : WearTimes.toEpochMillis(clip[1]),
                    Math.max(0, dur),
                    wearing,
                    WearTimes.toEpochMillis(s.getStartedAt()),
                    s.getEdited() != null && s.getEdited() == 1
            ));
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO updateSession(Long userId, Long sessionId, WearSessionEditRequest req) {
        WearSession session = wearSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.WEAR_SESSION_NOT_FOUND);
        }
        LocalDateTime now = WearTimes.now();
        LocalDate date = session.getStartedAt().toLocalDate();
        boolean wearing = session.getEndedAt() == null;

        LocalDateTime newStart = parseDateTime(date, req.start(), "戴上时间");
        LocalDateTime newEnd;
        if (wearing) {
            // 佩戴中：只允许改开始时间，结束保持空（结束佩戴走主按钮「我摘下了」）
            if (req.end() != null && !req.end().isBlank()) {
                throw new BusinessException(ErrorCode.WEAR_EDIT_FORBIDDEN, "佩戴中的会话不能设置结束时间，请走「我摘下了」");
            }
            newEnd = null;
        } else {
            newEnd = parseDateTime(date, req.end(), "摘下时间");
            if (!newEnd.isAfter(newStart)) {
                throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "摘下时刻需晚于戴上时刻");
            }
        }
        // 未来时间（仅今天）与跨日结束
        if (date.equals(now.toLocalDate()) && (newStart.isAfter(now) || (newEnd != null && newEnd.isAfter(now)))) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "时间不能晚于当前时刻");
        }
        if (newEnd != null && !newEnd.toLocalDate().equals(date)) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "结束时间不得跨到次日（跨夜请在起始日编辑）");
        }

        boolean startChanged = !session.getStartedAt().equals(newStart);
        boolean endChanged = !Objects.equals(session.getEndedAt(), newEnd);
        boolean changed = startChanged || endChanged;
        if (changed && hasOverlapExcluding(userId, newStart, newEnd, sessionId)) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_OVERLAP);
        }
        // 单日总时长 ≤24h（按有效会话集重算，涵盖本次修改）
        if (dayTotalAfterEdit(userId, date, sessionId, session.getSource(), newStart, newEnd) > 24L * 3600) {
            throw new BusinessException(ErrorCode.WEAR_MAKEUP_TIME_INVALID, "单日佩戴时长不能超过 24 小时");
        }

        // 留痕（改前值），MANUAL 编辑后置「修正」标
        if (startChanged) {
            logEdit(userId, sessionId, "TIME_START", "started_at", session.getStartedAt(), newStart);
            session.setStartedAt(newStart);
        }
        if (endChanged && newEnd != null) {
            logEdit(userId, sessionId, "TIME_END", "ended_at", session.getEndedAt(), newEnd);
            session.setEndedAt(newEnd);
        }
        if (changed && WearSourceEnum.MANUAL.getCode().equals(session.getSource())) {
            session.setEdited(1);
        }
        wearSessionMapper.updateById(session);
        log.info("佩戴会话编辑, userId={}, sessionId={}, {}-{}", userId, sessionId, newStart, newEnd);
        wearSettleService.settleDay(userId, date);
        return readToday(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayWearVO deleteSession(Long userId, Long sessionId) {
        WearSession session = wearSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.WEAR_SESSION_NOT_FOUND);
        }
        if (!WearSourceEnum.MAKEUP.getCode().equals(session.getSource())) {
            throw new BusinessException(ErrorCode.WEAR_EDIT_FORBIDDEN, "仅补录段可删除");
        }
        LocalDate date = session.getMakeupFor() == null ? session.getStartedAt().toLocalDate() : session.getMakeupFor();
        logEdit(userId, sessionId, "DELETE", "", session.getStartedAt(), null);
        wearSessionMapper.deleteById(sessionId);
        log.info("佩戴补录段删除, userId={}, sessionId={}, date={}", userId, sessionId, date);
        wearSettleService.settleDay(userId, date);
        return readToday(userId);
    }

    // ── 内部读取 ────────────────────────────────────────────

    /**
     * 组装「佩戴」页今日工作台：先确保昨日已结算，再实时推导今日（含庆祝归档）。
     */
    private TodayWearVO readToday(Long userId) {
        wearSettleService.ensureYesterdaySettled(userId);
        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime dayStart = WearTimes.startOf(today);
        int goal = goalSec(userId);

        List<WearSession> sessions = sessionsOverlapping(userId, dayStart, now);
        Map<LocalDate, WearDayMath.DaySecs> dayMap = WearDayMath.distribute(sessions, today, today, now);
        WearDayMath.DaySecs ds = WearDayMath.dayOrZero(dayMap, today);

        WearSession open = null;
        List<TodaySessionVO> vos = new ArrayList<>();
        for (WearSession s : sessions) {
            if (s.getEndedAt() == null) {
                open = s;
            }
            LocalDateTime[] clip = WearDayMath.clip(s, today, now);
            if (clip == null) {
                continue;
            }
            boolean wearing = s.getEndedAt() == null;
            long dur = wearing
                    ? Duration.between(clip[0], now).getSeconds()
                    : Duration.between(clip[0], clip[1]).getSeconds();
            vos.add(new TodaySessionVO(
                    s.getId(),
                    s.getSource(),
                    s.getMakeupFor() == null ? null : s.getMakeupFor().toString(),
                    WearTimes.toEpochMillis(clip[0]),
                    clip[1] == null ? null : WearTimes.toEpochMillis(clip[1]),
                    Math.max(0, dur),
                    wearing,
                    // 会话真实起始：跨夜段据此刻画「昨 21:03」文案（startTime 已被裁剪到今日 00:00）
                    WearTimes.toEpochMillis(s.getStartedAt()),
                    s.getEdited() != null && s.getEdited() == 1
            ));
        }

        long wear = ds.wear();
        boolean full = wear >= goal;
        boolean partial = !full && wear >= goal - WearSettleServiceImpl.PARTIAL_OFFSET_SEC;
        boolean guard = open != null
                && Duration.between(open.getStartedAt(), now).getSeconds() >= goal;

        boolean newlyFull = false;
        if (full) {
            // 首次观测到今日达标时归档一次庆祝消息（notify 内部按同日去重，故仅首次为 true）
            newlyFull = reminderNotifyService.notify(
                    userId,
                    WearReminderTypeEnum.WEAR_CELEBRATE,
                    today,
                    "今日佩戴达标 🎉",
                    "今天已佩戴 " + WearDayMath.tenthHoursLabel(wear) + "，达成每日目标");
        }

        return new TodayWearVO(
                today.toString(),
                goal,
                wear,
                ds.manual(),
                ds.makeup(),
                WearDayMath.tenthHoursLabel(wear),
                WearDayMath.percent(wear, goal),
                full,
                partial,
                newlyFull,
                consecutiveFullDays(userId, today.minusDays(1)),
                open != null,
                open == null ? null : WearTimes.toEpochMillis(open.getStartedAt()),
                guard,
                vos
        );
    }

    /** 截至 {@code upToDate}（含）的连续 FULL 天数；非 FULL/缺失即中断。 */
    private int consecutiveFullDays(Long userId, LocalDate upToDate) {
        int count = 0;
        LocalDate d = upToDate;
        while (true) {
            WearDailySummary row = wearDailySummaryMapper.selectOne(new LambdaQueryWrapper<WearDailySummary>()
                    .eq(WearDailySummary::getUserId, userId)
                    .eq(WearDailySummary::getSummaryDate, d)
                    .last("LIMIT 1"));
            if (row == null || !WearDailySummaryLevelFull(row)) {
                break;
            }
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    private static boolean WearDailySummaryLevelFull(WearDailySummary row) {
        return "FULL".equals(row.getLevel());
    }

    /** 取当前佩戴中会话（至多一段），无则 null。 */
    private WearSession findOpenSession(Long userId) {
        return wearSessionMapper.selectOne(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .isNull(WearSession::getEndedAt)
                .orderByDesc(WearSession::getStartedAt)
                .last("LIMIT 1"));
    }

    /** 与候选区间是否重叠（边界相切不算；end 为 null 表示无限开放）。 */
    private boolean hasOverlap(Long userId, LocalDateTime start, LocalDateTime end) {
        List<WearSession> overlaps = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .lt(WearSession::getStartedAt, end == null ? OPEN_END_FAR_FUTURE : end)
                .and(w -> w.isNull(WearSession::getEndedAt).or().gt(WearSession::getEndedAt, start))
                .last("LIMIT 1"));
        return !overlaps.isEmpty();
    }

    /** 与候选区间是否重叠（排除某会话自身；end 为 null 表示无限开放）。 */
    private boolean hasOverlapExcluding(Long userId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<WearSession> overlaps = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .ne(WearSession::getId, excludeId)
                .lt(WearSession::getStartedAt, end == null ? OPEN_END_FAR_FUTURE : end)
                .and(w -> w.isNull(WearSession::getEndedAt).or().gt(WearSession::getEndedAt, start))
                .last("LIMIT 1"));
        return !overlaps.isEmpty();
    }

    /** 编辑后某自然日总佩戴秒（用新时间替换该会话后按日切分），用于单日 ≤24h 校验。 */
    private long dayTotalAfterEdit(Long userId, LocalDate date, Long sessionId, String source,
                                   LocalDateTime newStart, LocalDateTime newEnd) {
        LocalDateTime dayStart = WearTimes.startOf(date);
        List<WearSession> daySessions = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .lt(WearSession::getStartedAt, WearTimes.endOf(date))
                .and(w -> w.isNull(WearSession::getEndedAt).or().ge(WearSession::getEndedAt, dayStart)));
        List<WearSession> effective = new ArrayList<>(daySessions.size());
        for (WearSession s : daySessions) {
            if (s.getId().equals(sessionId)) {
                WearSession copy = new WearSession();
                copy.setUserId(userId);
                copy.setSource(source);
                copy.setStartedAt(newStart);
                copy.setEndedAt(newEnd);
                effective.add(copy);
            } else {
                effective.add(s);
            }
        }
        Map<LocalDate, WearDayMath.DaySecs> map = WearDayMath.distribute(effective, date, date, WearTimes.now());
        return WearDayMath.dayOrZero(map, date).wear();
    }

    /** 写入一条会话编辑留痕。 */
    private void logEdit(Long userId, Long sessionId, String editType, String field,
                         LocalDateTime before, LocalDateTime after) {
        WearSessionEditLog log = new WearSessionEditLog();
        log.setSessionId(sessionId);
        log.setUserId(userId);
        log.setEditType(editType);
        log.setField(field);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setPointsCost(0);
        wearSessionEditLogMapper.insert(log);
    }

    /** 某自然日的 HH:mm → LocalDateTime，缺失/格式非法抛错。 */
    private LocalDateTime parseDateTime(LocalDate date, String hm, String label) {
        if (hm == null || hm.isBlank()) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, label + "不能为空");
        }
        LocalTime t = parseTime(hm);
        if (t == null) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, label + "格式应为 HH:mm");
        }
        return date.atTime(t);
    }

    /** 查询与 [dayStart, cap) 可能相交的会话（含佩戴中），升序。 */
    private List<WearSession> sessionsOverlapping(Long userId, LocalDateTime dayStart, LocalDateTime cap) {
        return wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .lt(WearSession::getStartedAt, cap)
                .and(w -> w.isNull(WearSession::getEndedAt).or().ge(WearSession::getEndedAt, dayStart))
                .orderByAsc(WearSession::getStartedAt)
                .orderByAsc(WearSession::getId));
    }

    /** 组装一条会话实体（不落库）。 */
    private WearSession newSession(Long userId, WearSourceEnum source, LocalDate makeupFor, LocalDateTime start, LocalDateTime end) {
        WearSession s = new WearSession();
        s.setUserId(userId);
        s.setSource(source.getCode());
        s.setMakeupFor(makeupFor);
        s.setStartedAt(start);
        s.setEndedAt(end);
        return s;
    }

    /** 目标秒 → GoalVO（双精度小时数带 0.1 精度展示）。 */
    private GoalVO toGoalVO(int goalSec) {
        return new GoalVO(
                Math.round(goalSec / 360.0) / 10.0,
                goalSec,
                MIN_GOAL_SEC / 3600.0,
                MAX_GOAL_SEC / 3600.0,
                GOAL_STEP_SEC / 3600.0
        );
    }

    /** 读取当前目标（未设置默认 20h）。 */
    private int goalSec(Long userId) {
        Integer goal = currentSetting(userId) == null ? null : currentSetting(userId).getGoalSec();
        return goal == null || goal <= 0 ? WearSettleServiceImpl.DEFAULT_GOAL_SEC : goal;
    }

    private UserSetting currentSetting(Long userId) {
        return wearUserSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 懒创建 user_setting 行（仅 userId），并发唯一键冲突幂等兜底。 */
    private UserSetting ensureSettingRow(Long userId) {
        UserSetting row = currentSetting(userId);
        if (row != null) {
            return row;
        }
        UserSetting fresh = new UserSetting();
        fresh.setUserId(userId);
        try {
            wearUserSettingMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            UserSetting raced = currentSetting(userId);
            if (raced != null) {
                return raced;
            }
            throw e;
        }
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "日期缺失");
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
    }

    private static LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "时间格式应为 HH:mm");
        }
    }
}
