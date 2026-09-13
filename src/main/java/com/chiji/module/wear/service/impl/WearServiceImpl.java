package com.chiji.module.wear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.UserSetting;
import com.chiji.entity.WearDailySummary;
import com.chiji.entity.WearSession;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.enums.WearSourceEnum;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.mapper.WearDailySummaryMapper;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.mapper.WearUserSettingMapper;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.service.WearSettleService;
import com.chiji.module.wear.support.WearDayMath;
import com.chiji.module.wear.support.WearTimes;
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
    /** 校准补录可回补的最早自然日偏移（今天-7） */
    private static final long MAKEUP_BACK_DAYS = 7;

    private final WearSessionMapper wearSessionMapper;
    private final WearDailySummaryMapper wearDailySummaryMapper;
    private final WearUserSettingMapper wearUserSettingMapper;
    private final WearSettleService wearSettleService;
    private final AlignerService alignerService;
    private final ReminderNotifyService reminderNotifyService;

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
            }
            // 已有佩戴中会话则幂等返回（不重复开段）
        } else if ("WEAR_OFF".equals(action)) {
            if (open == null) {
                throw new BusinessException(ErrorCode.WEAR_ACTIVE_SESSION_NOT_FOUND);
            }
            open.setEndedAt(WearTimes.now());
            wearSessionMapper.updateById(open);
            log.info("佩戴打卡关, userId={}, sessionId={}", userId, open.getId());
        } else {
            throw new BusinessException(ErrorCode.WEAR_PARAM_INVALID, "打卡动作应为 WEAR_ON / WEAR_OFF");
        }
        return readToday(userId);
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
        // 只允许补录 [今天-7, 昨天]
        if (date.isBefore(today.minusDays(MAKEUP_BACK_DAYS)) || !date.isBefore(today)) {
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
        setting.setGoalSec(sec);
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
                    WearTimes.toEpochMillis(s.getStartedAt())
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
                .lt(WearSession::getStartedAt, end == null ? LocalDateTime.MAX : end)
                .and(w -> w.isNull(WearSession::getEndedAt).or().gt(WearSession::getEndedAt, start))
                .last("LIMIT 1"));
        return !overlaps.isEmpty();
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
