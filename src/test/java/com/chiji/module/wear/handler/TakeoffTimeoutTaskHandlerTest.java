package com.chiji.module.wear.handler;

import com.chiji.entity.DelayTask;
import com.chiji.module.delay.handler.DelayTaskRescheduleException;
import com.chiji.module.delay.handler.DelayTaskSkipException;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.message.service.TakeoffNotifyService;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.support.TakeoffTimeoutSpec;
import com.chiji.module.wear.support.WearTimes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摘下超时提醒下发 handler 测试：围栏跳过 / 预扣跳过（不回补）/ 下发成功 / 下发失败跳过 / payload 容错。
 */
@ExtendWith(MockitoExtension.class)
class TakeoffTimeoutTaskHandlerTest {

    private static final long USER_ID = 10001L;
    private static final long SESSION_ID = 20002L;

    @Mock
    private WearService wearService;
    @Mock
    private SubscribeQuotaService subscribeQuotaService;
    @Mock
    private TakeoffNotifyService takeoffNotifyService;

    private TakeoffTimeoutTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TakeoffTimeoutTaskHandler(wearService, subscribeQuotaService, takeoffNotifyService,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private static DelayTask task(String payload) {
        DelayTask task = new DelayTask();
        task.setTaskType(TakeoffTimeoutTaskHandler.TASK_TYPE);
        task.setBizKey("takeoff-timeout:" + SESSION_ID);
        task.setPayload(payload);
        return task;
    }

    private static final String PAYLOAD = """
            {"userId":10001,"sessionId":20002,"remindMode":"HALF_HOUR",
             "takeoffAtText":"2026-10-08 10:00","thing4":"已摘下超过半小时，记得戴回"}
            """;

    @Test
    void fence_closed_session_skips_without_consuming_quota() {
        // 已戴回 / 已换副：业务围栏命中，跳过且不预扣额度
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(false);

        DelayTaskSkipException e = assertThrows(DelayTaskSkipException.class,
                () -> handler.execute(task(PAYLOAD)));
        assertTrue(e.getMessage().contains("戴回"));
        verify(subscribeQuotaService, never()).consumeIfAvailable(anyLong(), anyString());
        verify(takeoffNotifyService, never()).sendAndArchive(any());
    }

    @Test
    void no_daily_quota_skips_without_notify() {
        // 当日无可用额度：预扣 false → skip（预扣模型不回补，也不下发）
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(false);

        DelayTaskSkipException e = assertThrows(DelayTaskSkipException.class,
                () -> handler.execute(task(PAYLOAD)));
        assertTrue(e.getMessage().contains("额度"));
        verify(takeoffNotifyService, never()).sendAndArchive(any());
    }

    @Test
    void success_path_consumes_once_and_sends() {
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(true, 0, "ok"));

        assertDoesNotThrow(() -> handler.execute(task(PAYLOAD)));

        verify(subscribeQuotaService).consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT);
        verify(takeoffNotifyService).sendAndArchive(eq(new TakeoffNotifyService.SendCommand(
                USER_ID, "HALF_HOUR", "2026-10-08 10:00", "已摘下超过半小时，记得戴回")));
    }

    @Test
    void send_failure_skips_as_business_result_without_retry() {
        // 微信拒绝（如 43101）：业务性失败 → skip 记 last_error、不重试；额度已预扣不回补
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(false, 43101, "user refused"));

        DelayTaskSkipException e = assertThrows(DelayTaskSkipException.class,
                () -> handler.execute(task(PAYLOAD)));
        assertTrue(e.getMessage().contains("43101"));
        assertTrue(e.getMessage().contains("不回补"));
    }

    @Test
    void broken_payload_skips_safely() {
        // payload 缺 userId/sessionId：跳过且不触库
        DelayTaskSkipException e = assertThrows(DelayTaskSkipException.class,
                () -> handler.execute(task("{\"remindMode\":\"SMART\"}")));
        assertTrue(e.getMessage().contains("payload"));

        DelayTaskSkipException e2 = assertThrows(DelayTaskSkipException.class,
                () -> handler.execute(task(null)));
        assertTrue(e2.getMessage().contains("payload"));
        verify(wearService, never()).isTakeoffSessionOpen(anyLong(), anyLong());
    }

    // ── 智能模式跨零点重算（PRD 2026-10-08 新口径） ─────────────

    /** SMART 模式 payload（B20 场景：10-08 23:58 摘下，含 takeoffAtMs）。 */
    private static final String SMART_PAYLOAD = """
            {"userId":10001,"sessionId":20002,"remindMode":"SMART",
             "takeoffAtMs":%d,"takeoffAtText":"2026-10-08 23:58",
             "goalSec":72000,"wornSec":64800,"immediate":false,
             "thing4":"摘下剩余时间还剩5分钟，记得戴回"}
            """.formatted(LocalDateTime.of(2026, 10, 8, 23, 58)
            .atZone(WearTimes.ZONE).toInstant().toEpochMilli());

    @Test
    void b20_cross_day_reroutes_to_new_day_ledger() throws Exception {
        // B20：旧 executeAt = 次日 00:18（旧快照 25min - 5min 延期），到点时已跨零点
        // → 按新一天账本重算：free = 85320 - 72000 - 1200 = 12120s > 300
        // → DelayTaskRescheduleException 改期至 00:18 + (12120-300)s = 03:35（任务行不复制，仅 1 条）
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 0, 18);
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(wearService.currentGoalSec(USER_ID)).thenReturn(20 * 3600);

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            DelayTaskRescheduleException e = assertThrows(DelayTaskRescheduleException.class,
                    () -> handler.execute(task(SMART_PAYLOAD)));
            assertEquals(LocalDateTime.of(2026, 10, 9, 3, 35), e.getNewExecuteAt());
            assertTrue(e.getReason().contains("跨零点"));
            // 重排随行改写 payload（2026-10-08 文案拍板）：thing4 → 跨零点可见性文案 + 重排标记，
            // 到点凭标记跳过重算、直接下发新文案
            assertNotNull(e.getNewPayload());
            assertTrue(e.getNewPayload().contains(TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE));
            assertTrue(e.getNewPayload().contains(TakeoffTimeoutSpec.PAYLOAD_KEY_CROSS_DAY_RESCHEDULED));
        }
        // 改期不是重复下发：未预扣、未下发，下发至多 1 条由改期通道保证（同一任务行 PENDING 重排）
        verify(subscribeQuotaService, never()).consumeIfAvailable(anyLong(), anyString());
        verify(takeoffNotifyService, never()).sendAndArchive(any());
    }

    @Test
    void rescheduled_refire_sends_reschedule_copy_without_recalc() throws Exception {
        // 跨零点重排后到点（03:35）：payload thing4 已改写为跨零点可见性文案且带 crossDayRescheduled=true
        // 标记 → 跳过重算分支（摘下日期恒为昨日，无标记会二次进入重算：公式随时间双重递减使剩余
        // 必然 ≤5min，错误改发「不足」即达文案），直接下发重排文案、不查新目标
        String payload = """
                {"userId":10001,"sessionId":20002,"remindMode":"SMART",
                 "takeoffAtMs":%d,"takeoffAtText":"2026-10-08 23:58",
                 "goalSec":72000,"wornSec":64800,"immediate":false,
                 "thing4":"%s","crossDayRescheduled":true}
                """.formatted(LocalDateTime.of(2026, 10, 8, 23, 58)
                .atZone(WearTimes.ZONE).toInstant().toEpochMilli(),
                TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE);
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(true, 0, "ok"));

        // 不 mockStatic WearTimes：标记路径不取当前时刻（STRICT_STUBS 会判无用 stub）
        assertDoesNotThrow(() -> handler.execute(task(payload)));

        verify(wearService, never()).currentGoalSec(anyLong());
        verify(takeoffNotifyService).sendAndArchive(eq(new TakeoffNotifyService.SendCommand(
                USER_ID, "SMART", "2026-10-08 23:58",
                TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE)));
    }

    @Test
    void b23_cross_day_free_le_5min_sends_cross_day_copy_immediately() {
        // B23：到点时已跨零点，重算 free = 82800 - 72000 - 10500 = 300s ≤ 5min
        // → 立即下发跨零点文案（走预扣 + 留痕原链）
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 1, 0);
        String payload = """
                {"userId":10001,"sessionId":20002,"remindMode":"SMART",
                 "takeoffAtMs":%d,"takeoffAtText":"2026-10-08 22:05",
                 "goalSec":72000,"wornSec":64800,"immediate":false,
                 "thing4":"摘下剩余时间还剩5分钟，记得戴回"}
                """.formatted(LocalDateTime.of(2026, 10, 8, 22, 5)
                .atZone(WearTimes.ZONE).toInstant().toEpochMilli());
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(wearService.currentGoalSec(USER_ID)).thenReturn(20 * 3600);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(true, 0, "ok"));

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            assertDoesNotThrow(() -> handler.execute(task(payload)));
        }
        verify(takeoffNotifyService).sendAndArchive(eq(new TakeoffNotifyService.SendCommand(
                USER_ID, "SMART", "2026-10-08 22:05",
                TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY)));
    }

    @Test
    void cross_day_reroute_uses_current_goal_not_snapshot() {
        // 重算必须用「新目标」（用户可能已改）：currentGoalSec 返回 21h（与 payload 快照 20h 不同）
        // now 00:30 → 24:00 剩余 84600s；本次已摘（23:00→00:30）= 5400s
        // → free = 84600 - 75600 - 5400 = 3600s → 改期至 00:30 + (3600-300)s = 01:25
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 0, 30);
        String payload = """
                {"userId":10001,"sessionId":20002,"remindMode":"SMART",
                 "takeoffAtMs":%d,"takeoffAtText":"2026-10-08 23:00",
                 "goalSec":72000,"wornSec":64800,"immediate":false,
                 "thing4":"摘下剩余时间还剩5分钟，记得戴回"}
                """.formatted(LocalDateTime.of(2026, 10, 8, 23, 0)
                .atZone(WearTimes.ZONE).toInstant().toEpochMilli());
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(wearService.currentGoalSec(USER_ID)).thenReturn(21 * 3600);

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            DelayTaskRescheduleException e = assertThrows(DelayTaskRescheduleException.class,
                    () -> handler.execute(task(payload)));
            assertEquals(LocalDateTime.of(2026, 10, 9, 1, 25), e.getNewExecuteAt());
        }
    }

    @Test
    void cross_day_without_goal_skips_with_last_error() {
        // 防御分支：新目标未设置（≤0）→ skip 记 last_error，不预扣不下发
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 0, 18);
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(wearService.currentGoalSec(USER_ID)).thenReturn(0);

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            DelayTaskSkipException e = assertThrows(DelayTaskSkipException.class,
                    () -> handler.execute(task(SMART_PAYLOAD)));
            assertTrue(e.getMessage().contains("新目标未设置"));
        }
        verify(subscribeQuotaService, never()).consumeIfAvailable(anyLong(), anyString());
        verify(takeoffNotifyService, never()).sendAndArchive(any());
    }

    @Test
    void smart_same_day_execution_keeps_original_path() {
        // 未跨日（摘下日期 = 当前日期）：即使 SMART 模式也走原路径（payload 快照文案）
        LocalDateTime now = LocalDateTime.of(2026, 10, 8, 14, 0);
        String payload = """
                {"userId":10001,"sessionId":20002,"remindMode":"SMART",
                 "takeoffAtMs":%d,"takeoffAtText":"2026-10-08 13:30",
                 "goalSec":72000,"wornSec":64800,"immediate":false,
                 "thing4":"摘下剩余时间还剩5分钟，记得戴回"}
                """.formatted(LocalDateTime.of(2026, 10, 8, 13, 30)
                .atZone(WearTimes.ZONE).toInstant().toEpochMilli());
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(true, 0, "ok"));

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            assertDoesNotThrow(() -> handler.execute(task(payload)));
        }
        verify(takeoffNotifyService).sendAndArchive(eq(new TakeoffNotifyService.SendCommand(
                USER_ID, "SMART", "2026-10-08 13:30", "摘下剩余时间还剩5分钟，记得戴回")));
    }

    @Test
    void fixed_mode_cross_midnight_keeps_original_logic() {
        // 固定模式跨零点连续计时不变（23:45 摘下固定半小时，次日 00:15 到点）：
        // 固定文案不含时间信息不失真 → 不走重算，直接预扣下发
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 0, 15);
        when(wearService.isTakeoffSessionOpen(USER_ID, SESSION_ID)).thenReturn(true);
        when(subscribeQuotaService.consumeIfAvailable(USER_ID, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT))
                .thenReturn(true);
        when(takeoffNotifyService.sendAndArchive(any(TakeoffNotifyService.SendCommand.class)))
                .thenReturn(new TakeoffNotifyService.SendOutcome(true, 0, "ok"));

        try (MockedStatic<WearTimes> mocked = mockStatic(WearTimes.class)) {
            mocked.when(WearTimes::now).thenReturn(now);
            assertDoesNotThrow(() -> handler.execute(task(PAYLOAD)));
        }
        verify(takeoffNotifyService).sendAndArchive(any(TakeoffNotifyService.SendCommand.class));
        verify(wearService, never()).currentGoalSec(anyLong());
    }
}
