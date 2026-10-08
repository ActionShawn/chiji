package com.chiji.module.wear.service.impl;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.entity.UserSetting;
import com.chiji.entity.WearSession;
import com.chiji.enums.WearSourceEnum;
import com.chiji.module.delay.service.DelayTaskService;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.wear.mapper.WearDailySummaryMapper;
import com.chiji.module.wear.mapper.WearSessionEditLogMapper;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.mapper.WearUserSettingMapper;
import com.chiji.module.wear.service.WearSettleService;
import com.chiji.module.wear.support.WearTimes;
import com.chiji.module.wear.vo.ConsultSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复诊小结统计口径测试：目标起算（无基准不评判）、goal_updated_at 基准优先与 NULL 回退、
 * 跨零点切分、后端权威三态（DATA / STAGE_NO_RECORD / NO_STAGE）、ALIGNER 档最后一副回落、
 * 已结束阶段窗口收口、recordDays 直出、副进度，及目标写路径（updateGoal 刷新 goalUpdatedAt
 * 仅限值变化）。
 * <p>
 * WearServiceImpl 的 9 个依赖全部 mock；WearDayMath.distribute 为真实静态调用
 * （与生产共用同一套跨零点切分口径）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultSummaryTest {

    private static final Long USER_ID = 10001L;
    private static final Long ALIGNER_ID = 9L;
    private static final Long STAGE_ID = 7L;
    private static final int GOAL = 20 * 3600;

    @Mock
    private WearSessionMapper wearSessionMapper;
    @Mock
    private WearSessionEditLogMapper wearSessionEditLogMapper;
    @Mock
    private WearDailySummaryMapper wearDailySummaryMapper;
    @Mock
    private WearUserSettingMapper wearUserSettingMapper;
    @Mock
    private WearSettleService wearSettleService;
    @Mock
    private AlignerService alignerService;
    @Mock
    private ReminderNotifyService reminderNotifyService;
    @Mock
    private DelayTaskService delayTaskService;
    @Mock
    private NotificationSettingService notificationSettingService;

    private WearServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WearServiceImpl(wearSessionMapper, wearSessionEditLogMapper, wearDailySummaryMapper,
                wearUserSettingMapper, wearSettleService, alignerService, reminderNotifyService,
                delayTaskService, notificationSettingService);
    }

    private void stubSetting(LocalDateTime updatedAt) {
        stubSetting(updatedAt, null);
    }

    private void stubSetting(LocalDateTime updatedAt, LocalDateTime goalUpdatedAt) {
        UserSetting setting = new UserSetting();
        setting.setUserId(USER_ID);
        setting.setGoalSec(GOAL);
        setting.setUpdatedAt(updatedAt);
        setting.setGoalUpdatedAt(goalUpdatedAt);
        when(wearUserSettingMapper.selectOne(any())).thenReturn(setting);
    }

    private void stubActiveAligner(LocalDate startDate) {
        stubActiveAligner(startDate, null);
    }

    private void stubActiveAligner(LocalDate startDate, LocalDate endDate) {
        Aligner aligner = new Aligner();
        aligner.setId(ALIGNER_ID);
        aligner.setStageId(STAGE_ID);
        aligner.setNum(2);
        aligner.setStartDate(startDate);
        aligner.setEndDate(endDate);
        aligner.setTotalDays(14);
        when(alignerService.findActiveAligner(eq(USER_ID), eq((String) null))).thenReturn(aligner);
        when(alignerService.countAligners(STAGE_ID)).thenReturn(20);
        when(alignerService.alignerPlannedDays(ALIGNER_ID)).thenReturn(14);
    }

    private static Aligner aligner(Long stageId, int num, LocalDate start, LocalDate end, String state) {
        Aligner a = new Aligner();
        a.setId(ALIGNER_ID);
        a.setStageId(stageId);
        a.setNum(num);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setState(state);
        a.setTotalDays(14);
        return a;
    }

    private static Stage stage(Long id, String name, LocalDate start, String status) {
        Stage s = new Stage();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setName(name);
        s.setStartDate(start);
        s.setStatus(status);
        return s;
    }

    private static WearSession session(LocalDateTime start, LocalDateTime end) {
        WearSession s = new WearSession();
        s.setUserId(USER_ID);
        s.setStartedAt(start);
        s.setEndedAt(end);
        return s;
    }

    @Test
    void invalid_period_throws_param_error() {
        assertThrows(BusinessException.class, () -> service.consultSummary(USER_ID, "WEEK", null));
        assertThrows(BusinessException.class, () -> service.consultSummary(USER_ID, null, null));
    }

    @Test
    void stage_without_stage_returns_structured_empty() {
        // 有副但 STAGE 周期解析不到阶段：结构化空标记，不抛错
        stubSetting(WearTimes.now().minusDays(3));
        when(alignerService.resolveStage(USER_ID, null)).thenReturn(null);
        when(alignerService.findActiveAligner(eq(USER_ID), eq((String) null))).thenReturn(null);

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "STAGE", null);
        assertTrue(vo.empty());
        assertEquals(ConsultSummaryVO.STATE_NO_STAGE, vo.viewState());
        assertEquals(0, vo.totalSec());
        assertEquals(0, vo.underCount());
        assertNull(vo.aligner());
        assertNull(vo.stage());
        assertNull(vo.startDate());
    }

    @Test
    void last30_without_sessions_returns_structured_empty() {
        // 周期内无任何打卡：empty=true 且副进度照常返回（有副 → 态C 而非 NO_STAGE）
        stubSetting(WearTimes.now().minusDays(3));
        stubActiveAligner(WearTimes.now().toLocalDate().minusDays(4));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of());

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertTrue(vo.empty());
        assertEquals(ConsultSummaryVO.STATE_STAGE_NO_RECORD, vo.viewState());
        assertNotNull(vo.aligner());
        assertEquals(2, vo.aligner().num());
        assertEquals(20, vo.aligner().total());
        assertEquals(5, vo.aligner().wornDays());
        assertEquals(14, vo.aligner().plannedDays());
    }

    @Test
    void under_days_respect_goal_since_baseline() {
        // 目标基准日 = 今天（updatedAt=今晨）：昨天及之前「无基准不评判」，仅今日计入未达标
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().withHour(0).withMinute(5));
        stubActiveAligner(today.minusDays(4));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(2).atTime(8, 0), today.minusDays(2).atTime(10, 0)),   // 2h，基准日前不评判
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(10, 0)),   // 2h，基准日前不评判
                session(today.atTime(8, 0), today.atTime(9, 0))                                // 今日 1h < 目标 → 未达标
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(1, vo.underCount());
        assertEquals(1, vo.underDays().size());
        assertEquals(today.toString(), vo.underDays().get(0).date());
        assertEquals(3600, vo.underDays().get(0).wearSec());
        assertEquals(GOAL, vo.underDays().get(0).goalSec());
        assertTrue(vo.inProgress());
        // 日均 = 有佩戴日（3 天）总时长 / 3：7200 + 7200 + 3600 = 18000
        assertEquals(6000, vo.avgSec());
    }

    @Test
    void cross_midnight_session_splits_by_natural_day() {
        // 跨零点会话 23:50 → 次日 00:10：前 10 分钟归昨日、后 10 分钟归今日，不重复计时
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(3));
        stubActiveAligner(today.minusDays(4));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(1).atTime(23, 50), today.atTime(0, 10))
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(1200, vo.totalSec());
        assertEquals(600, vo.underDays().stream()
                .filter(d -> d.date().equals(today.toString()))
                .findFirst().orElseThrow().wearSec());
        assertEquals(600, vo.underDays().stream()
                .filter(d -> d.date().equals(today.minusDays(1).toString()))
                .findFirst().orElseThrow().wearSec());
    }

    @Test
    void aligner_period_uses_aligner_start_date() {
        // ALIGNER 周期：从本副开始日起算（早于副开始的会话不计入）
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(10));
        stubActiveAligner(today.minusDays(2));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(3).atTime(8, 0), today.minusDays(3).atTime(12, 0)),  // 副开始前
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(12, 0))   // 4h
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "ALIGNER", null);
        assertEquals(4L * 3600, vo.totalSec());
        // 副开始日 = 前天：本副开始日起至今自然日数含今日 = 3 天
        assertEquals(3, vo.aligner().wornDays());
        // 周期 3 天均 < 目标（20h）→ 3 个未达标日；副开始前（前天-1 的 4h）不进周期不计未达标
        assertEquals(3, vo.underCount());
        assertTrue(vo.underDays().stream().noneMatch(d -> d.date().equals(today.minusDays(3).toString())));
    }

    // ── BE-8 三态与周期收口（C9 / C10 / C11 / C12 / C13 / C15） ─────────

    @Test
    void aligner_period_falls_back_to_stage_last_aligner_when_stage_ended() {
        // C9：阶段已结束无进行中副 → ALIGNER 档回落「本阶段最后一副全程」，完整历史不再被遮蔽
        //（修复前：from=today 且今日无打卡 → empty=true + aligner=null，医生面前事实性错误）
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(40));
        when(alignerService.findActiveAligner(eq(USER_ID), eq((String) null))).thenReturn(null);
        // 无 ACTIVE 阶段（已全部结束）且未传 stageId → 阶段经「用户最近一副」回落解析
        when(alignerService.resolveStage(eq(USER_ID), eq((Long) null))).thenReturn(null);
        Aligner last = aligner(STAGE_ID, 3, today.minusDays(16), today.minusDays(3), "DONE");
        when(alignerService.findLatestAlignerOfUser(USER_ID)).thenReturn(last);
        Stage ended = stage(STAGE_ID, "第一阶段 · 主力矫正", today.minusDays(30), "ENDED");
        when(alignerService.resolveStage(eq(USER_ID), eq(STAGE_ID))).thenReturn(ended);
        when(alignerService.countAligners(STAGE_ID)).thenReturn(20);
        when(alignerService.alignerPlannedDays(ALIGNER_ID)).thenReturn(14);
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(14).atTime(8, 0), today.minusDays(14).atTime(11, 0)),  // 3h
                session(today.minusDays(8).atTime(9, 0), today.minusDays(8).atTime(13, 0))     // 4h
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "ALIGNER", null);
        assertEquals(ConsultSummaryVO.STATE_DATA, vo.viewState());
        assertFalse(vo.empty());
        // 周期 = 最后一副全程（today-16..today-3，共 14 天）：已收口 → endDate 直出、inProgress=false
        assertEquals(today.minusDays(16).toString(), vo.startDate());
        assertEquals(today.minusDays(3).toString(), vo.endDate());
        assertFalse(vo.inProgress());
        assertEquals(2, vo.recordDays());
        assertEquals(7L * 3600, vo.totalSec());
        // 窗口 14 天全部早于/落在目标基准日后且均 < 20h → 全部计入未达标（窗口双端证明）
        assertEquals(14, vo.underCount());
        assertNotNull(vo.aligner());
        assertEquals(3, vo.aligner().num());
        assertEquals(today.minusDays(3).toString(), vo.aligner().endDate());
        // wornDays 保持既有口径：自本副开始日起至今含今日 = 17 天，不随副收口按结束日截断
        assertEquals(17, vo.aligner().wornDays());
        assertNotNull(vo.stage());
        assertEquals(STAGE_ID, vo.stage().id());
        assertEquals("第一阶段 · 主力矫正", vo.stage().name());
        assertEquals(today.minusDays(3).toString(), vo.stage().endDate());
    }

    @Test
    void aligner_in_progress_reports_null_end_and_in_progress() {
        // C10：进行中副 → aligner.endDate=null、顶层 endDate=null、inProgress=true（真实判定替换原恒 true）
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(10));
        stubActiveAligner(today.minusDays(2));
        Stage activeStage = stage(STAGE_ID, "第一阶段 · 主力矫正", today.minusDays(20), "ACTIVE");
        when(alignerService.resolveStage(eq(USER_ID), eq(STAGE_ID))).thenReturn(activeStage);
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(12, 0))  // 4h
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "ALIGNER", null);
        assertEquals(ConsultSummaryVO.STATE_DATA, vo.viewState());
        assertTrue(vo.inProgress());
        assertNull(vo.endDate());
        assertEquals(today.minusDays(2).toString(), vo.startDate());
        assertNotNull(vo.aligner());
        assertNull(vo.aligner().endDate());
        assertEquals(today.minusDays(2).toString(), vo.aligner().startDate());
        // 窗口 3 天中仅昨日有佩戴 → 记录天数 = 1
        assertEquals(1, vo.recordDays());
        assertEquals(4L * 3600, vo.totalSec());
    }

    @Test
    void record_days_direct_output_counts_days_with_wear() {
        // C11：recordDays 直出 = 周期内有佩戴记录的天数（含补录）；与 aligner.wornDays（副自然日
        // 口径，自本副开始日起至今含今日）不同名不混用——两者允许不等
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(10));
        stubActiveAligner(today.minusDays(4));
        WearSession manual = session(today.minusDays(2).atTime(7, 0), today.minusDays(2).atTime(9, 0));
        manual.setSource(WearSourceEnum.MANUAL.getCode());   // 打卡 2h
        WearSession makeup = session(today.minusDays(1).atTime(21, 0), today.minusDays(1).atTime(23, 0));
        makeup.setSource(WearSourceEnum.MAKEUP.getCode());   // 补录 2h
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(manual, makeup));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(ConsultSummaryVO.STATE_DATA, vo.viewState());
        assertEquals(2, vo.recordDays());
        assertEquals(4L * 3600, vo.totalSec());
        // 日均按「有佩戴日」平均：4h / 2 天 = 2h
        assertEquals(2L * 3600, vo.avgSec());
        // 副口径 wornDays = 5 天（today-4 起至今含今日）≠ recordDays = 2 天
        assertEquals(5, vo.aligner().wornDays());
    }

    @Test
    void stage_with_no_records_returns_stage_no_record_state() {
        // C12：有阶段但所选周期内零打卡（新阶段未开始佩戴）→ STAGE_NO_RECORD，阶段卡信息照常返回
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(3));
        stubActiveAligner(today.minusDays(1));
        Stage activeStage = stage(STAGE_ID, "第一阶段 · 主力矫正", today.minusDays(1), "ACTIVE");
        when(alignerService.resolveStage(eq(USER_ID), eq((Long) null))).thenReturn(activeStage);
        when(wearSessionMapper.selectList(any())).thenReturn(List.of());

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "STAGE", null);
        assertEquals(ConsultSummaryVO.STATE_STAGE_NO_RECORD, vo.viewState());
        assertTrue(vo.empty());
        // 无数据 → 周期起止置空，由阶段卡承载区间展示
        assertNull(vo.startDate());
        assertNull(vo.endDate());
        assertEquals(0, vo.recordDays());
        assertNotNull(vo.stage());
        assertEquals(STAGE_ID, vo.stage().id());
        assertEquals("第一阶段 · 主力矫正", vo.stage().name());
        assertEquals(today.minusDays(1).toString(), vo.stage().startDate());
        assertNull(vo.stage().endDate());
        assertNotNull(vo.aligner());
        // 进行中阶段窗口含今日 → inProgress 真实判定为 true
        assertTrue(vo.inProgress());
    }

    @Test
    void never_created_stage_returns_no_stage_state() {
        // C13：从未创建阶段 → NO_STAGE 全空（LAST30 档同样生效，避免「无阶段有数据」歧义）
        stubSetting(WearTimes.now().minusDays(3));
        when(alignerService.findActiveAligner(eq(USER_ID), eq((String) null))).thenReturn(null);
        when(alignerService.resolveStage(eq(USER_ID), eq((Long) null))).thenReturn(null);
        when(alignerService.findLatestAlignerOfUser(USER_ID)).thenReturn(null);

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(ConsultSummaryVO.STATE_NO_STAGE, vo.viewState());
        assertTrue(vo.empty());
        assertNull(vo.startDate());
        assertNull(vo.endDate());
        assertNull(vo.stage());
        assertNull(vo.aligner());
        assertEquals(0, vo.totalSec());
        assertEquals(0, vo.recordDays());
        assertFalse(vo.inProgress());
    }

    @Test
    void ended_stage_window_excludes_later_stage_sessions() {
        // C15：已结束阶段 STAGE 档窗口收口至阶段结束日（最后一副结束日），后续阶段的会话不混入
        //（修复前：窗口到 now，结束日后开启的新阶段会话混入本阶段统计，口径污染）
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().minusDays(40));
        when(alignerService.findActiveAligner(eq(USER_ID), eq((String) null))).thenReturn(null);
        Stage ended = stage(STAGE_ID, "第一阶段 · 主力矫正", today.minusDays(30), "ENDED");
        when(alignerService.resolveStage(eq(USER_ID), eq(STAGE_ID))).thenReturn(ended);
        Aligner last = aligner(STAGE_ID, 3, today.minusDays(23), today.minusDays(10), "DONE");
        when(alignerService.findLastAlignerOfStage(STAGE_ID)).thenReturn(last);
        when(alignerService.countAligners(STAGE_ID)).thenReturn(20);
        when(alignerService.alignerPlannedDays(ALIGNER_ID)).thenReturn(14);
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(15).atTime(8, 0), today.minusDays(15).atTime(10, 0)),  // 阶段内 2h
                session(today.minusDays(5).atTime(8, 0), today.minusDays(5).atTime(11, 0)),    // 结束日后 3h
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(9, 0))      // 结束日后 1h
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "STAGE", STAGE_ID);
        assertEquals(ConsultSummaryVO.STATE_DATA, vo.viewState());
        assertEquals(today.minusDays(30).toString(), vo.startDate());
        assertEquals(today.minusDays(10).toString(), vo.endDate());
        assertFalse(vo.inProgress());
        // 仅阶段内 1 个记录日、总时长 2h——结束日之后的 3h/1h 不混入
        assertEquals(1, vo.recordDays());
        assertEquals(2L * 3600, vo.totalSec());
        // 窗口 [today-30, today-10] 共 21 天全部纳入评判（基准日 40 天前）→ 21 个未达标日，
        // 且全部不晚于阶段结束日（双端证明窗口收口）
        assertEquals(21, vo.underCount());
        assertTrue(vo.underDays().stream()
                .noneMatch(d -> d.date().compareTo(today.minusDays(10).toString()) > 0));
        assertEquals(today.minusDays(10).toString(), vo.stage().endDate());
        assertEquals(today.minusDays(10).toString(), vo.aligner().endDate());
    }

    // ── goal_updated_at 基准（PRD 2026-10-08 边界 2 拍板） ─────────

    @Test
    void goal_updated_at_takes_precedence_over_row_updated_at() {
        // 分叉断言：整行今天因其他设置项保存过（updatedAt=今晨），但目标 2 天前设置
        // → 基准日必须取 goalUpdatedAt（前天）。distribute 含周期内全部自然日（0 佩戴天 wear=0
        //   同样参与未达标评判），故评判范围 = 前天/昨天/今天共 3 天均 < 20h 目标 → underCount=3；
        //   若实现错误沿用 updatedAt（今晨），评判范围只剩 today → underCount=1，本断言即失败
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().withHour(0).withMinute(5),
                WearTimes.now().minusDays(2).toLocalDate().atTime(10, 0));
        stubActiveAligner(today.minusDays(5));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(3).atTime(8, 0), today.minusDays(3).atTime(10, 0)), // 基准日前 → 不评判
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(10, 0))  // 基准日后 → 未达标
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(3, vo.underCount());
        assertTrue(vo.underDays().stream().anyMatch(d -> d.date().equals(today.minusDays(1).toString())),
                "基准日后（goalUpdatedAt 口径）的昨天必须计入未达标");
        assertTrue(vo.underDays().stream().noneMatch(d -> d.date().equals(today.minusDays(3).toString())),
                "基准日前（目标设置之前）不追溯评判");
    }

    @Test
    void null_goal_updated_at_falls_back_to_row_updated_at() {
        // 存量行回退：goalUpdatedAt=NULL（迁移前旧数据）→ 基准日回退 updated_at 近似
        // （宽松方向：整行更新日偏晚 → 少评判）——整行今晨更新 → 基准日=今天，
        // 昨天 2h 在基准日前不评判；today 0 佩戴 → 仅 today 计入未达标
        LocalDate today = WearTimes.now().toLocalDate();
        stubSetting(WearTimes.now().withHour(0).withMinute(5), null);
        stubActiveAligner(today.minusDays(5));
        when(wearSessionMapper.selectList(any())).thenReturn(List.of(
                session(today.minusDays(1).atTime(8, 0), today.minusDays(1).atTime(10, 0))
        ));

        ConsultSummaryVO vo = service.consultSummary(USER_ID, "LAST30", null);
        assertEquals(1, vo.underCount());
        assertEquals(today.toString(), vo.underDays().get(0).date());
    }

    // ── 目标写路径：updateGoal 刷新 goalUpdatedAt ─────────────────

    @Test
    void update_goal_change_refreshes_goal_updated_at() {
        // 目标变化：保存新值同时写 goalUpdatedAt=now（写路径 PRD 边界 2 的另一半）
        LocalDateTime oldGoalAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        stubSetting(WearTimes.now().minusDays(1), oldGoalAt);

        service.updateGoal(USER_ID, 21.0);

        ArgumentCaptor<UserSetting> captor = ArgumentCaptor.forClass(UserSetting.class);
        verify(wearUserSettingMapper).updateById(captor.capture());
        assertEquals(21 * 3600, captor.getValue().getGoalSec());
        assertNotNull(captor.getValue().getGoalUpdatedAt());
        assertTrue(captor.getValue().getGoalUpdatedAt().isAfter(oldGoalAt),
                "目标变化必须刷新 goalUpdatedAt");
    }

    @Test
    void update_goal_same_value_keeps_goal_updated_at() {
        // 仅目标变化时刷新：提交相同值不重置基准日（否则未达标评判起点被人为推迟）
        LocalDateTime oldGoalAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        stubSetting(WearTimes.now().minusDays(1), oldGoalAt);

        service.updateGoal(USER_ID, 20.0);

        ArgumentCaptor<UserSetting> captor = ArgumentCaptor.forClass(UserSetting.class);
        verify(wearUserSettingMapper).updateById(captor.capture());
        assertEquals(GOAL, captor.getValue().getGoalSec());
        assertEquals(oldGoalAt, captor.getValue().getGoalUpdatedAt(),
                "目标值未变不得重置 goalUpdatedAt");
    }
}
