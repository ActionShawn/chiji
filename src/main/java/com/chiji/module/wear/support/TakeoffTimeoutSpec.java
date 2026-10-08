package com.chiji.module.wear.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 摘下超时提醒投递规格（纯静态函数，无 Spring 依赖，供单测直测）。
 * <p>
 * 覆盖 PRD 4.1 摘下超时提醒的投递口径：
 * <ul>
 *   <li>固定模式：摘下时刻 + 30 / 60 分钟触发；</li>
 *   <li>智能模式：摘下时刻快照一次性计算——可摘下时间 = 今日 24:00 剩余 −（今日目标 − 今日已佩戴），
 *       剩 5 分钟点触发（executeAt = 摘下 + 可摘下时间 − 5min）；可摘下时间 ≤ 5 分钟视为
 *       「摘下即达上限」，executeAt = 摘下 + 1s（走调度泵正常路径，不在 punch 事务内同步下发）；</li>
 *   <li>触发时刻可能落在次日（跨零点场景），不按自然日截断；</li>
 *   <li>thing4 各模式最终文案（≤20 字符）见 {@link #thing4Of}。</li>
 * </ul>
 * 业务键：{@code takeoff-timeout:<sessionId>}，戴回 / 换副时按同键幂等取消。
 */
public final class TakeoffTimeoutSpec {

    /** 固定提醒任务类型 / 订阅场景码（与 SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT 同名） */
    public static final String TASK_TYPE = "TAKEOFF_TIMEOUT";

    /** 延迟任务业务键前缀 */
    public static final String BIZ_KEY_PREFIX = "takeoff-timeout:";

    /** 智能模式「剩 5 分钟点触发」阈值（秒） */
    public static final long SMART_MIN_FREE_SEC = 300;

    /** 摘下即达上限时的最短延迟（秒）：走调度泵正常路径，避免 punch 事务内同步下发 */
    public static final long IMMEDIATE_DELAY_SEC = 1;

    /** 固定半小时模式的延迟（秒） */
    public static final long HALF_HOUR_DELAY_SEC = Duration.ofMinutes(30).toSeconds();

    /** 固定一小时模式的延迟（秒） */
    public static final long ONE_HOUR_DELAY_SEC = Duration.ofMinutes(60).toSeconds();

    /** 模板 thing1 固定文案 */
    public static final String THING1 = "牙套摘下超时提醒";

    /** thing4：固定半小时模式文案（≤20 字符） */
    public static final String THING4_HALF_HOUR = "已摘下超过半小时，记得戴回";

    /** thing4：固定一小时模式文案（≤20 字符） */
    public static final String THING4_ONE_HOUR = "已摘下超过一小时，记得戴回";

    /** thing4：智能模式（非即达）文案（≤20 字符） */
    public static final String THING4_SMART = "摘下剩余时间还剩5分钟，记得戴回";

    /** thing4：智能模式「摘下即达上限」文案（≤20 字符） */
    public static final String THING4_SMART_IMMEDIATE = "今日剩余可摘时间不足，记得戴回";

    /** thing4：智能模式「跨零点即达上限」文案（≤20 字符） */
    public static final String THING4_SMART_CROSS_DAY = "已跨零点，剩余可摘时间不足，记得戴回";

    /** thing4：智能模式「跨零点重排后到点」可见性文案（2026-10-08 文案拍板：重算改期导致的
     * 时机变化须让用户看懂「账过零点了」，≤20 字符） */
    public static final String THING4_SMART_CROSS_DAY_RESCHEDULE = "已跨零点，摘下剩余时间5分钟，记得戴回";

    /** payload 标记 key：本任务已经过跨零点重排（重排时由 handler 写入 true；
     * 到点后不再重算，直接下发 {@link #THING4_SMART_CROSS_DAY_RESCHEDULE}，
     * 防止重排后的到点因摘下日期恒为昨日而二次进入重算分支） */
    public static final String PAYLOAD_KEY_CROSS_DAY_RESCHEDULED = "crossDayRescheduled";

    /** 提醒方式：固定半小时 */
    public static final String MODE_HALF_HOUR = "HALF_HOUR";
    /** 模式：固定一小时 */
    public static final String MODE_ONE_HOUR = "ONE_HOUR";
    /** 模式：智能提醒 */
    public static final String MODE_SMART = "SMART";

    private static final DateTimeFormatter TAKEOFF_AT_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 智能模式跨零点重算结果（二选一）：按新一天账本重排到 {@code rescheduleAt}，
     * 或立即下发跨零点即达文案（{@code sendImmediately=true}）。
     */
    public record CrossDayReroute(LocalDateTime rescheduleAt, boolean sendImmediately) {

        /** 立即下发跨零点文案 */
        public static CrossDayReroute sendNow() {
            return new CrossDayReroute(null, true);
        }

        /** 重排至新时刻（到点以 {@link #THING4_SMART_CROSS_DAY_RESCHEDULE} 跨零点可见性文案下发，
         * 由 handler 在重排时写入 payload） */
        public static CrossDayReroute defer(LocalDateTime at) {
            return new CrossDayReroute(at, false);
        }
    }

    /**
     * 智能模式跨零点重算（PRD 2026-10-08 修订：可摘下时间仅针对当日）。
     * <p>
     * 公式与 PRD 逐项对应：
     * <ol>
     *   <li>新可摘下时间（未扣本次摘下）= 新一天 24:00 前剩余 −（新目标 − 新一天已佩戴）；</li>
     *   <li>再扣减跨零点连续计时的本次已摘时长 = −（{@code now} − {@code takeoffStartedAt}）；</li>
     *   <li>剩余 &gt; {@link #SMART_MIN_FREE_SEC} → 重排至 {@code now + 剩余 − 5min}
     *       （重排路径到点下发跨零点可见性文案 {@link #THING4_SMART_CROSS_DAY_RESCHEDULE}，
     *       2026-10-08 文案拍板——凌晨收到普通文案用户无法理解提醒时刻为何与摘下时被告知的不同）；</li>
     *   <li>剩余 ≤ 5 分钟（含 ≤0）→ 立即下发跨零点即达文案 {@link #THING4_SMART_CROSS_DAY}。</li>
     * </ol>
     * 「每次摘下事件最多 1 条下发」跨零点不重置：重排不复制任务行；重排时刻经
     * {@code free ≤ 24:00 剩余} 数学保证落在当日界内（理论不会二次跨日），防御性兜底
     * 见界外分支（立即下发，最简且不重复下发）。
     *
     * @param now              当前时刻（任务到点执行时刻，Asia/Shanghai）
     * @param newGoalSec       新一天佩戴目标（秒，来自用户当前设置，非摘下时快照）
     * @param wornSecNewDay    新一天已佩戴时长（秒，不含本次跨零点连续摘下）
     * @param takeoffStartedAt 本次摘下开始时刻（摘下事件从昨日延续，时长连续）
     * @return 重排结果（重排时刻 或 立即下发标记）
     */
    public static CrossDayReroute crossDayReroute(LocalDateTime now, int newGoalSec,
                                                  long wornSecNewDay, LocalDateTime takeoffStartedAt) {
        long untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
        long alreadyOffSec = Math.max(0, Duration.between(takeoffStartedAt, now).getSeconds());
        long freeSec = untilMidnight - (newGoalSec - wornSecNewDay) - alreadyOffSec;
        if (freeSec <= SMART_MIN_FREE_SEC) {
            return CrossDayReroute.sendNow();
        }
        LocalDateTime rescheduleAt = now.plusSeconds(freeSec - SMART_MIN_FREE_SEC);
        // 防御性兜底：重排时刻理论上必在当日界内（freeSec ≤ untilMidnight 恒成立）；
        // 若极端场景界外，不再延期，立即下发一次即终止（不重复下发）
        if (!rescheduleAt.toLocalDate().equals(now.toLocalDate())) {
            return CrossDayReroute.sendNow();
        }
        return CrossDayReroute.defer(rescheduleAt);
    }

    private TakeoffTimeoutSpec() {
    }

    /** 延迟任务业务键：{@code takeoff-timeout:<sessionId>} */
    public static String bizKey(Long sessionId) {
        return BIZ_KEY_PREFIX + sessionId;
    }

    /**
     * thing4 最终文案（按提醒方式；智能模式区分「摘下即达上限」）。
     *
     * @param remindMode HALF_HOUR / ONE_HOUR / SMART
     * @param immediate  智能模式是否摘下即达上限（其余模式忽略）
     * @return 模板 thing4 文案（≤20 字符）；未知方式按固定半小时
     */
    public static String thing4Of(String remindMode, boolean immediate) {
        if (MODE_SMART.equals(remindMode)) {
            return immediate ? THING4_SMART_IMMEDIATE : THING4_SMART;
        }
        return MODE_ONE_HOUR.equals(remindMode) ? THING4_ONE_HOUR : THING4_HALF_HOUR;
    }

    /**
     * 智能模式「摘下即达上限」判定：可摘下时间 ≤ 5 分钟（快照以摘下时刻一次性计算）。
     *
     * @param takeoffAt    摘下时刻
     * @param goalSec      每日佩戴目标（秒）
     * @param wornSecToday 摘下时刻的今日已佩戴时长（秒，与统计侧同一跨零点切分口径）
     * @return true 表示摘下即达上限
     */
    public static boolean isSmartImmediate(LocalDateTime takeoffAt, int goalSec, long wornSecToday) {
        return smartFreeSec(takeoffAt, goalSec, wornSecToday) <= SMART_MIN_FREE_SEC;
    }

    /**
     * 智能模式可摘下时间（秒）= 今日 24:00 剩余 −（今日目标 − 今日已佩戴）。
     * <p>
     * 已佩戴超过目标时结果可大于「24:00 剩余」（今日已达标，剩余时间全部可自由摘戴），
     * 不会为负——佩戴时长上限（22h）小于一天。
     *
     * @param takeoffAt    摘下时刻
     * @param goalSec      每日佩戴目标（秒）
     * @param wornSecToday 摘下时刻的今日已佩戴时长（秒）
     * @return 可摘下时间（秒）
     */
    public static long smartFreeSec(LocalDateTime takeoffAt, int goalSec, long wornSecToday) {
        long untilMidnight = Duration.between(takeoffAt, takeoffAt.toLocalDate().plusDays(1).atStartOfDay())
                .getSeconds();
        return untilMidnight - (goalSec - wornSecToday);
    }

    /**
     * 计算延迟任务触发时刻（摘下时刻快照一次性算好，不随佩戴状态变化）。
     *
     * @param remindMode   HALF_HOUR / ONE_HOUR / SMART；未知方式按固定半小时
     * @param takeoffAt    摘下时刻
     * @param goalSec      每日佩戴目标（秒，仅智能模式使用）
     * @param wornSecToday 摘下时刻的今日已佩戴时长（秒，仅智能模式使用）
     * @return 触发时刻（智能模式可跨零点落在次日）
     */
    public static LocalDateTime calcExecuteAt(String remindMode, LocalDateTime takeoffAt,
                                              int goalSec, long wornSecToday) {
        if (MODE_SMART.equals(remindMode)) {
            long freeSec = smartFreeSec(takeoffAt, goalSec, wornSecToday);
            if (freeSec <= SMART_MIN_FREE_SEC) {
                return takeoffAt.plusSeconds(IMMEDIATE_DELAY_SEC);
            }
            return takeoffAt.plusSeconds(freeSec - SMART_MIN_FREE_SEC);
        }
        if (MODE_ONE_HOUR.equals(remindMode)) {
            return takeoffAt.plusSeconds(ONE_HOUR_DELAY_SEC);
        }
        return takeoffAt.plusSeconds(HALF_HOUR_DELAY_SEC);
    }

    /**
     * 组装延迟任务 payload（下发 handler 所需全部信息一次性携带，执行时不再回查快照）。
     *
     * @param userId       用户 ID
     * @param sessionId    摘下动作收口的佩戴会话 ID（业务键与围栏依据）
     * @param remindMode   提醒方式
     * @param takeoffAt    摘下时刻
     * @param goalSec      每日佩戴目标（秒）
     * @param wornSecToday 摘下时刻的今日已佩戴时长（秒）
     * @param immediate    智能模式是否摘下即达上限
     * @return payload（thing4 文案按 remindMode/immediate 预先定稿）
     */
    public static Map<String, Object> buildPayload(Long userId, Long sessionId, String remindMode,
                                                   LocalDateTime takeoffAt, int goalSec,
                                                   long wornSecToday, boolean immediate) {
        long freeSec = MODE_SMART.equals(remindMode)
                ? Math.max(smartFreeSec(takeoffAt, goalSec, wornSecToday), 0) : -1;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("sessionId", sessionId);
        payload.put("remindMode", remindMode);
        payload.put("takeoffAtMs", takeoffAt.atZone(WearTimes.ZONE).toInstant().toEpochMilli());
        payload.put("takeoffAtText", takeoffAt.format(TAKEOFF_AT_TEXT));
        payload.put("goalSec", goalSec);
        payload.put("wornSec", wornSecToday);
        payload.put("freeMin", freeSec < 0 ? -1 : freeSec / 60);
        payload.put("immediate", immediate);
        payload.put("thing4", thing4Of(remindMode, immediate));
        return payload;
    }
}
