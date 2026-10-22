package com.chiji.module.wear.controller;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.wear.dto.GoalUpdateRequest;
import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.dto.WearPunchRequest;
import com.chiji.module.wear.dto.WearSessionEditRequest;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.vo.ConsultSummaryVO;
import com.chiji.module.wear.vo.GoalVO;
import com.chiji.module.wear.vo.TodayWearVO;
import com.chiji.module.wear.vo.WearAlignerSummaryVO;
import com.chiji.module.wear.vo.WearStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 佩戴接口固定托槽统一门控测试（Controller 级拦截）。
 * <p>
 * 覆盖：固定托槽用户调佩戴侧任一接口返回专用业务错误码
 * {@code WEAR_FIXED_BRACKET_NOT_APPLICABLE(70011)} 且不触达业务 service；
 * 隐形用户全接口正常路径回归（门控 false 短路，service 返回值原样透传，行为零变化）。
 * <p>
 * 纯 Mockito 单测：mockStatic {@link SecurityUtil} 提供登录用户，mock {@link WearService}
 * 隔离 mapper 与业务写路径；不起 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WearControllerGateTest {

    private static final Long USER_ID = 10001L;
    private static final int GATE_CODE = ErrorCode.WEAR_FIXED_BRACKET_NOT_APPLICABLE.getCode();
    private static final String GATE_MESSAGE = "当前矫正方式无需佩戴打卡";

    @Mock
    private WearService wearService;

    private WearController controller;

    @BeforeEach
    void setUp() {
        controller = new WearController(wearService);
    }

    /** 全部 13 个佩戴接口的调用方式（含入参 DTO 构造），供固定/隐形两侧循环复用。 */
    private List<NamedEndpoint> allEndpoints() {
        WearMakeupRequest makeupReq = new WearMakeupRequest("2026-10-20", List.of(), "CLEAR_SINGLE");
        WearMorningBackfillRequest morningReq =
                new WearMorningBackfillRequest("2026-10-20", "23:00", true, null, "CLEAR_SINGLE");
        WearSessionEditRequest editReq = new WearSessionEditRequest("08:00", "12:00");
        return List.of(
                new NamedEndpoint("today", () -> controller.today()),
                new NamedEndpoint("punch", () -> controller.punch(new WearPunchRequest("WEAR_ON", "CLEAR_SINGLE"))),
                new NamedEndpoint("cancelActive", () -> controller.cancelActive()),
                new NamedEndpoint("makeup", () -> controller.makeup(makeupReq)),
                new NamedEndpoint("morning", () -> controller.morning(morningReq)),
                new NamedEndpoint("sessions", () -> controller.sessions("2026-10-21")),
                new NamedEndpoint("updateSession", () -> controller.updateSession(1L, editReq)),
                new NamedEndpoint("deleteSession", () -> controller.deleteSession(1L)),
                new NamedEndpoint("getGoal", () -> controller.getGoal()),
                new NamedEndpoint("updateGoal", () -> controller.updateGoal(new GoalUpdateRequest(20.0))),
                new NamedEndpoint("stats", () -> controller.stats("LAST_7", null)),
                new NamedEndpoint("alignerSummary", () -> controller.alignerSummary(9L)),
                new NamedEndpoint("consultSummary", () -> controller.consultSummary("LAST30", null))
        );
    }

    private record NamedEndpoint(String name, Runnable call) {
    }

    // ── 固定托槽用户：统一拦截 ───────────────────────────────────────────

    @Test
    void fixed_user_punch_rejected_with_gate_error() {
        when(wearService.isFixedBracketUser(USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.punch(new WearPunchRequest("WEAR_ON", "CLEAR_SINGLE")));
            assertEquals(GATE_CODE, ex.getCode());
            assertEquals(GATE_MESSAGE, ex.getMessage());
        }
        // 门控先行短路：业务打卡链路一次都不可触达
        verify(wearService, never()).punch(any(), any(), any());
    }

    @Test
    void fixed_user_all_endpoints_rejected_and_service_never_invoked() {
        when(wearService.isFixedBracketUser(USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);

            for (NamedEndpoint endpoint : allEndpoints()) {
                BusinessException ex = assertThrows(BusinessException.class, endpoint.call()::run,
                        "接口 " + endpoint.name() + " 未被门控拦截");
                assertEquals(GATE_CODE, ex.getCode(), "接口 " + endpoint.name() + " 错误码不符");
                assertEquals(GATE_MESSAGE, ex.getMessage(), "接口 " + endpoint.name() + " 文案不符");
            }
        }
        // 13 个接口均先过门控（各 1 次判定），且没有任何业务方法被触达
        verify(wearService, times(13)).isFixedBracketUser(USER_ID);
        verify(wearService, never()).punch(any(), any(), any());
        verify(wearService, never()).today(any());
        verify(wearService, never()).cancelActive(any());
        verify(wearService, never()).makeup(any(), any());
        verify(wearService, never()).morningBackfill(any(), any());
        verify(wearService, never()).sessions(any(), any());
        verify(wearService, never()).updateSession(any(), any(), any());
        verify(wearService, never()).deleteSession(any(), any());
        verify(wearService, never()).getGoal(any());
        verify(wearService, never()).updateGoal(any(), any());
        verify(wearService, never()).stats(any(), any(), any());
        verify(wearService, never()).alignerSummary(any(), any());
        verify(wearService, never()).consultSummary(any(), any(), any());
    }

    // ── 隐形用户：正常路径零变化回归 ─────────────────────────────────────

    @Test
    void clear_user_punch_normal_path_unchanged() {
        when(wearService.isFixedBracketUser(USER_ID)).thenReturn(false);
        TodayWearVO vo = wearVo();
        when(wearService.punch(USER_ID, "WEAR_ON", "CLEAR_SINGLE")).thenReturn(vo);
        when(wearService.punch(USER_ID, "WEAR_OFF", "CLEAR_DUAL")).thenReturn(vo);
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);

            R<TodayWearVO> on = controller.punch(new WearPunchRequest("WEAR_ON", "CLEAR_SINGLE"));
            assertEquals(ErrorCode.SUCCESS.getCode(), on.code());
            assertSame(vo, on.data(), "戴上打卡返回值必须原样透传");

            R<TodayWearVO> off = controller.punch(new WearPunchRequest("WEAR_OFF", "CLEAR_DUAL"));
            assertEquals(ErrorCode.SUCCESS.getCode(), off.code());
            assertSame(vo, off.data(), "摘下打卡返回值必须原样透传");
        }
        verify(wearService, times(1)).punch(USER_ID, "WEAR_ON", "CLEAR_SINGLE");
        verify(wearService, times(1)).punch(USER_ID, "WEAR_OFF", "CLEAR_DUAL");
        // 每次打卡仅 1 次门控判定（false 短路，无额外副作用）
        verify(wearService, times(2)).isFixedBracketUser(USER_ID);
    }

    @Test
    void clear_user_all_endpoints_normal_path_unchanged() {
        when(wearService.isFixedBracketUser(USER_ID)).thenReturn(false);
        TodayWearVO vo = wearVo();
        GoalVO goal = new GoalVO(20.0, 72000, 18.0, 22.0, 0.5);
        WearStatsVO stats = new WearStatsVO("LAST_7", List.of(), List.of(), 72000, 0, 0, 0, 0L, null, 0L);
        WearAlignerSummaryVO aligner = new WearAlignerSummaryVO(9L, 0L, 0, 0, List.of());
        ConsultSummaryVO consult = new ConsultSummaryVO("LAST30", "DATA", false, null, null,
                0, null, 0L, 0L, 0, List.of(), 72000L, false, null);

        when(wearService.today(USER_ID)).thenReturn(vo);
        when(wearService.punch(eq(USER_ID), eq("WEAR_ON"), eq("CLEAR_SINGLE"))).thenReturn(vo);
        when(wearService.cancelActive(USER_ID)).thenReturn(vo);
        when(wearService.makeup(eq(USER_ID), any(WearMakeupRequest.class))).thenReturn(vo);
        when(wearService.morningBackfill(eq(USER_ID), any(WearMorningBackfillRequest.class))).thenReturn(vo);
        when(wearService.sessions(USER_ID, LocalDate.parse("2026-10-21"))).thenReturn(List.of());
        when(wearService.updateSession(eq(USER_ID), eq(1L), any(WearSessionEditRequest.class))).thenReturn(vo);
        when(wearService.deleteSession(USER_ID, 1L)).thenReturn(vo);
        when(wearService.getGoal(USER_ID)).thenReturn(goal);
        when(wearService.updateGoal(USER_ID, 20.0)).thenReturn(goal);
        when(wearService.stats(USER_ID, "LAST_7", null)).thenReturn(stats);
        when(wearService.alignerSummary(USER_ID, 9L)).thenReturn(aligner);
        when(wearService.consultSummary(USER_ID, "LAST30", null)).thenReturn(consult);

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);

            for (NamedEndpoint endpoint : allEndpoints()) {
                endpoint.call().run();
            }
        }
        // 13 个接口各 1 次门控判定（隐形用户 false 短路），透传无重排
        verify(wearService, times(13)).isFixedBracketUser(USER_ID);
        verify(wearService, times(1)).punch(USER_ID, "WEAR_ON", "CLEAR_SINGLE");
        verify(wearService, times(1)).today(USER_ID);
        verify(wearService, times(1)).sessions(USER_ID, LocalDate.parse("2026-10-21"));
        verify(wearService, times(1)).consultSummary(USER_ID, "LAST30", null);
    }

    /** 构造带标记值的今日工作台视图（断言同一实例透传，字段仅用于区分非空）。 */
    private static TodayWearVO wearVo() {
        return new TodayWearVO("2026-10-22", 72000, 1L, 1L, 0L, "0.0h", 0.0,
                false, false, false, 0, false, null, false, List.of());
    }
}
