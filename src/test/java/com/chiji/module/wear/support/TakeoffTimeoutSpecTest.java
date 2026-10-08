package com.chiji.module.wear.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘下超时提醒投递规格测试：executeAt 计算（固定/智能/即达上限/跨零点）、thing4 文案、业务键、payload。
 */
class TakeoffTimeoutSpecTest {

    private static final int GOAL_20H = 20 * 3600;

    // ── 固定模式 ────────────────────────────────────────────

    @Test
    void fixed_half_hour_adds_30_minutes() {
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        assertEquals(takeoff.plusMinutes(30),
                TakeoffTimeoutSpec.calcExecuteAt(TakeoffTimeoutSpec.MODE_HALF_HOUR, takeoff, GOAL_20H, 0));
    }

    @Test
    void fixed_one_hour_adds_60_minutes() {
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        assertEquals(takeoff.plusMinutes(60),
                TakeoffTimeoutSpec.calcExecuteAt(TakeoffTimeoutSpec.MODE_ONE_HOUR, takeoff, GOAL_20H, 0));
    }

    @Test
    void unknown_mode_falls_back_to_half_hour() {
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        assertEquals(takeoff.plusMinutes(30),
                TakeoffTimeoutSpec.calcExecuteAt("WHATEVER", takeoff, GOAL_20H, 0));
    }

    // ── 智能模式 ────────────────────────────────────────────

    @Test
    void smart_triggers_when_free_time_minus_5_minutes() {
        // 10:00 摘下，今日已佩戴 6h，目标 20h → 剩余目标 14h，距 24:00 还有 14h
        // 可摘下时间 = 14h - 14h = 0 → 即达上限（摘下即达标剩余耗尽）
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        assertTrue(TakeoffTimeoutSpec.isSmartImmediate(takeoff, GOAL_20H, 6 * 3600));
        assertEquals(takeoff.plusSeconds(1),
                TakeoffTimeoutSpec.calcExecuteAt(TakeoffTimeoutSpec.MODE_SMART, takeoff, GOAL_20H, 6 * 3600));
    }

    @Test
    void smart_free_time_exactly_5_minutes_counts_as_immediate() {
        // 可摘下时间恰为 5 分钟：属于「≤ 5 分钟即达上限」边界
        // 24:00 剩余 = 2h；goal - worn = 1h55m → free = 5min
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 22, 0);
        assertTrue(TakeoffTimeoutSpec.isSmartImmediate(takeoff, GOAL_20H, 18 * 3600 + 5 * 60));
    }

    @Test
    void smart_normal_delay_is_free_time_minus_5_minutes() {
        // 10:00 摘下，已佩戴 2h，目标 20h → 剩余目标 18h，距 24:00 还有 14h
        // 可摘下时间 = 14h - 18h = 负 → 即达上限（今天戴满目标前已无可摘富余）
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        assertTrue(TakeoffTimeoutSpec.isSmartImmediate(takeoff, GOAL_20H, 2 * 3600));
    }

    @Test
    void smart_cross_midnight_execute_at_lands_next_day() {
        // PRD B20 场景：23:58 摘下、可摘下 25 分钟 → 触发点 = 摘下 + (25min - 5min) = 次日 00:18，
        // executeAt 落在次日、不按自然日截断（PRD B15「剩 5 分钟点触发」口径）。
        // 24:00 剩余 2min；goal 20h；worn = goal + (1500s - 120s) = 20h + 1380s → free = 120 - 1380 反算：
        // free = untilMidnight - (goal - worn) = worn - goal + 120 = 1500 → worn = 20h + 1380
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 23, 58);
        long worn = GOAL_20H + 1380L;
        assertEquals(1500, TakeoffTimeoutSpec.smartFreeSec(takeoff, GOAL_20H, worn));
        assertFalse(TakeoffTimeoutSpec.isSmartImmediate(takeoff, GOAL_20H, worn));
        LocalDateTime expected = takeoff.plusSeconds(1500 - 300);
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 9, 0, 18), expected);
        assertEquals(expected, TakeoffTimeoutSpec.calcExecuteAt(TakeoffTimeoutSpec.MODE_SMART, takeoff, GOAL_20H, worn));
    }

    @Test
    void smart_already_over_goal_defers_to_midnight_side() {
        // 已佩戴超过目标：可摘下时间 > 距 24:00 剩余 → 触发点延后但不超过语义上限（正常公式值）
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 20, 0);
        long worn = GOAL_20H + 3600; // 已超目标 1h
        long free = TakeoffTimeoutSpec.smartFreeSec(takeoff, GOAL_20H, worn);
        // 距 24:00 还有 4h，free = 4h + 1h = 5h
        assertEquals(5 * 3600, free);
        assertEquals(takeoff.plusSeconds(5 * 3600 - 300),
                TakeoffTimeoutSpec.calcExecuteAt(TakeoffTimeoutSpec.MODE_SMART, takeoff, GOAL_20H, worn));
    }

    // ── 跨零点重算（PRD 2026-10-08 新口径：可摘下时间仅针对当日） ──

    @Test
    void b20_cross_day_reroute_recalculates_by_new_day_ledger() {
        // B20 场景（PRD 例）：10-08 23:58 摘下、旧快照可摘下 25min → executeAt = 次日 00:18。
        // 到点（10-09 00:18）按新一天账本重算：
        //   ① 24:00 剩余 = 23h42min = 85320s
        //   ② 新目标 20h，新一天已佩戴 0 → 85320 - 72000 = 13320
        //   ③ 再扣本次已摘（00:18 - 23:58 = 20min = 1200s）→ free = 12120s（3h22min）
        //   ④ free > 300 → 重排至 00:18 + (12120 - 300)s = 10-09 03:35:00
        LocalDateTime now = LocalDateTime.of(2026, Month.OCTOBER, 9, 0, 18);
        LocalDateTime takeoffStart = LocalDateTime.of(2026, Month.OCTOBER, 8, 23, 58);
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, GOAL_20H, 0L, takeoffStart);
        assertFalse(reroute.sendImmediately());
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 9, 3, 35), reroute.rescheduleAt());
        // 重排时刻落在新一天 24:00 界内（不二次跨日）
        assertTrue(reroute.rescheduleAt().toLocalDate().equals(now.toLocalDate()));
    }

    @Test
    void b23_cross_day_free_at_most_5_minutes_sends_immediately() {
        // B23 场景：重算后剩余恰为 5 分钟（≤5min 判定含边界）→ 立即下发跨零点文案。
        // now 01:00（24:00 剩余 82800s），新目标 20h，新一天已佩戴 0，
        // 本次已摘 01:00 - 22:05（昨日）= 2h55min = 10500s → free = 82800 - 72000 - 10500 = 300
        LocalDateTime now = LocalDateTime.of(2026, Month.OCTOBER, 9, 1, 0);
        LocalDateTime takeoffStart = LocalDateTime.of(2026, Month.OCTOBER, 8, 22, 5);
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, GOAL_20H, 0L, takeoffStart);
        assertTrue(reroute.sendImmediately());
    }

    @Test
    void cross_day_reroute_with_worn_time_in_new_day() {
        // 新一天已佩戴 > 0（重摘：新一天先佩戴后又摘下的重算口径完备性）：
        // now 01:00（82800s），新目标 20h，新一天已佩戴 2h → 82800 - (72000-7200) = 18000，
        // 再扣本次已摘（01:00 - 00:30 = 1800s）→ free = 16200s → 重排 01:00 + (16200-300)s = 05:25
        LocalDateTime now = LocalDateTime.of(2026, Month.OCTOBER, 9, 1, 0);
        LocalDateTime takeoffStart = LocalDateTime.of(2026, Month.OCTOBER, 9, 0, 30);
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, GOAL_20H, 2 * 3600L, takeoffStart);
        assertFalse(reroute.sendImmediately());
        assertEquals(LocalDateTime.of(2026, Month.OCTOBER, 9, 5, 25), reroute.rescheduleAt());
    }

    @Test
    void cross_day_reroute_negative_free_sends_immediately() {
        // 剩余为负（新目标远大于 24:00 剩余 + 已佩戴）：≤5 分钟判定 → 立即下发
        LocalDateTime now = LocalDateTime.of(2026, Month.OCTOBER, 9, 0, 30);
        LocalDateTime takeoffStart = LocalDateTime.of(2026, Month.OCTOBER, 8, 23, 0);
        // free = 23.5h - 22h - 1.5h = 0 → 立即下发
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, 22 * 3600, 0L, takeoffStart);
        assertTrue(reroute.sendImmediately());
    }

    @Test
    void cross_day_reroute_out_of_day_boundary_defensive_send_now() {
        // 防御性兜底：新一天已佩戴超过目标使 free > 24:00 剩余（数学上仅 worn > goal + off 可构造），
        // 重排时刻越过当日 24:00 → 不再延期，立即下发（最简且不重复下发）
        LocalDateTime now = LocalDateTime.of(2026, Month.OCTOBER, 9, 10, 0);
        // free = 14h - (20h - 21h) - 0 = 15h → 重排时刻 10:00 + 14h55min = 次日 00:55 界外
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, GOAL_20H, 21 * 3600L,
                        LocalDateTime.of(2026, Month.OCTOBER, 9, 10, 0));
        assertTrue(reroute.sendImmediately());
    }

    @Test
    void cross_day_copy_within_20_chars() {
        assertTrue(TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY.length() <= 20,
                "跨零点文案超长: " + TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY);
        assertEquals("已跨零点，剩余可摘时间不足，记得戴回", TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY);
        // 2026-10-08 文案拍板：跨零点重排后到点下发跨零点可见性文案（19 字符），
        // 凌晨用户须看懂「账过零点了」（提醒时刻与摘下时被告知的不同）
        assertTrue(TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE.length() <= 20,
                "跨零点重排文案超长: " + TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE);
        assertEquals("已跨零点，摘下剩余时间5分钟，记得戴回",
                TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE);
    }

    // ── 文案 / 业务键 / payload ─────────────────────────────

    @Test
    void thing4_copies_by_mode_and_length_limit() {
        assertEquals("已摘下超过半小时，记得戴回",
                TakeoffTimeoutSpec.thing4Of(TakeoffTimeoutSpec.MODE_HALF_HOUR, false));
        assertEquals("已摘下超过一小时，记得戴回",
                TakeoffTimeoutSpec.thing4Of(TakeoffTimeoutSpec.MODE_ONE_HOUR, false));
        assertEquals("摘下剩余时间还剩5分钟，记得戴回",
                TakeoffTimeoutSpec.thing4Of(TakeoffTimeoutSpec.MODE_SMART, false));
        assertEquals("今日剩余可摘时间不足，记得戴回",
                TakeoffTimeoutSpec.thing4Of(TakeoffTimeoutSpec.MODE_SMART, true));
        // 微信 thing 字段硬性 ≤ 20 字符
        for (String copy : new String[]{
                TakeoffTimeoutSpec.THING4_HALF_HOUR, TakeoffTimeoutSpec.THING4_ONE_HOUR,
                TakeoffTimeoutSpec.THING4_SMART, TakeoffTimeoutSpec.THING4_SMART_IMMEDIATE}) {
            assertTrue(copy.length() <= 20, "thing4 文案超长: " + copy);
        }
    }

    @Test
    void biz_key_uses_session_id() {
        assertEquals("takeoff-timeout:12345", TakeoffTimeoutSpec.bizKey(12345L));
    }

    @Test
    void payload_carries_snapshot_for_handler() {
        LocalDateTime takeoff = LocalDateTime.of(2026, Month.OCTOBER, 8, 10, 0);
        Map<String, Object> payload = TakeoffTimeoutSpec.buildPayload(
                1L, 2L, TakeoffTimeoutSpec.MODE_SMART, takeoff, GOAL_20H, 6 * 3600, true);
        assertEquals(1L, payload.get("userId"));
        assertEquals(2L, payload.get("sessionId"));
        assertEquals(TakeoffTimeoutSpec.MODE_SMART, payload.get("remindMode"));
        assertEquals("2026-10-08 10:00", payload.get("takeoffAtText"));
        assertEquals(GOAL_20H, ((Number) payload.get("goalSec")).longValue());
        assertEquals(6 * 3600L, ((Number) payload.get("wornSec")).longValue());
        assertEquals(true, payload.get("immediate"));
        assertEquals("今日剩余可摘时间不足，记得戴回", payload.get("thing4"));
    }
}
