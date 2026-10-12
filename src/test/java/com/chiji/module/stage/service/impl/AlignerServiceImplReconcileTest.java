package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.WearSession;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.enums.RecordModeEnum;
import com.chiji.enums.WearSourceEnum;
import com.chiji.module.delay.service.DelayTaskService;
import com.chiji.module.message.service.ProgressReminderService;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.service.assembler.AlignerNodeAssembler;
import com.chiji.module.wear.mapper.WearSessionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlignerServiceImpl#reconcileAligners} 编辑阶段对齐逻辑单测（Mockito，不依赖 DB）。
 * <p>
 * 重点回归「用户使用一段时间后编辑阶段」的数据保护语义：
 * <ul>
 *   <li>未显式修改开始日期：当前副保持既有 startDate（佩戴天数动态推算不被破坏），
 *       前序 DONE 副的真实佩戴历史日期不被重排为理论值</li>
 *   <li>显式修改开始日期：仅当前副及之后从新日期重排，前序 DONE 副日期保持不动</li>
 *   <li>起始副调小：其后的副已有佩戴记录/印记时拒绝回退</li>
 *   <li>起始副前移且佩戴中：拆分 open 会话归属新副（与换副路径同语义）</li>
 *   <li>阶段始终无开始日期：不清空已手动排期的日期</li>
 * </ul>
 * 断言方式：排期步骤 in-place 修改 existing 列表持有的同一对象，直接断言对象字段；
 * 新会话经 ArgumentCaptor 捕获 insert 参数断言。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlignerServiceImplReconcileTest {

    private static final LocalDate D0901 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D0908 = LocalDate.of(2026, 9, 8);
    private static final LocalDate D0912 = LocalDate.of(2026, 9, 12);
    private static final LocalDate D0913 = LocalDate.of(2026, 9, 13);
    private static final LocalDate D0916 = LocalDate.of(2026, 9, 16);
    private static final LocalDate D0917 = LocalDate.of(2026, 9, 17);
    private static final LocalDate D0920 = LocalDate.of(2026, 9, 20);
    private static final LocalDate D0921 = LocalDate.of(2026, 9, 21);
    private static final LocalDate D0923 = LocalDate.of(2026, 9, 23);
    private static final LocalDate D0924 = LocalDate.of(2026, 9, 24);
    private static final LocalDate D1001 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D1008 = LocalDate.of(2026, 10, 8);

    @Mock
    private AlignerMapper alignerMapper;
    @Mock
    private StageMapper stageMapper;
    @Mock
    private AlignerNodeAssembler alignerNodeAssembler;
    @Mock
    private ProgressReminderService progressReminderService;
    @Mock
    private TimelineRecordMapper timelineRecordMapper;
    @Mock
    private WearSessionMapper wearSessionMapper;
    @Mock
    private DelayTaskService delayTaskService;

    @InjectMocks
    private AlignerServiceImpl service;

    /**
     * 纯单测无 MyBatis 启动流程：LambdaUpdateWrapper.set() 即时解析实体列名，
     * 需预先注册涉及实体的 TableInfo 缓存（与生产 MybatisPlus 启动时自动扫描等效）。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.chiji.module.stage.service.impl.AlignerServiceImplReconcileTest");
        TableInfoHelper.initTableInfo(assistant, Aligner.class);
        TableInfoHelper.initTableInfo(assistant, Stage.class);
        TableInfoHelper.initTableInfo(assistant, WearSession.class);
        TableInfoHelper.initTableInfo(assistant, TimelineRecord.class);
    }

    // ── 测试脚手架 ──────────────────────────────────────────────

    /** 单模阶段：count 副、每副 7 天。 */
    private Stage stage(int count, LocalDate startDate) {
        Stage s = new Stage();
        s.setId(100L);
        s.setUserId(1L);
        s.setMode(RecordModeEnum.CLEAR_SINGLE.getCode());
        s.setCount(count);
        s.setDaysPerAligner(7);
        s.setStartDate(startDate);
        return s;
    }

    private Aligner node(long id, int num, AlignerStateEnum state, LocalDate start, LocalDate end) {
        Aligner a = new Aligner();
        a.setId(id);
        a.setStageId(100L);
        a.setNum(num);
        a.setState(state.getCode());
        a.setTotalDays(7);
        a.setStartDate(start);
        a.setEndDate(end);
        return a;
    }

    /** 6 副阶段、副 3 ACTIVE 的常规存量数据（各副日期可按用例覆写）。 */
    private List<Aligner> existing() {
        List<Aligner> list = new ArrayList<>();
        list.add(node(1, 1, AlignerStateEnum.DONE, D0901, D0908));
        list.add(node(2, 2, AlignerStateEnum.DONE, D0913, D0916));
        list.add(node(3, 3, AlignerStateEnum.ACTIVE, D0917, D0923));
        list.add(node(4, 4, AlignerStateEnum.FUTURE, D0924, LocalDate.of(2026, 9, 30)));
        list.add(node(5, 5, AlignerStateEnum.FUTURE, D1001, LocalDate.of(2026, 10, 7)));
        list.add(node(6, 6, AlignerStateEnum.FUTURE, D1008, LocalDate.of(2026, 10, 14)));
        return list;
    }

    private void stubExisting(List<Aligner> list) {
        when(alignerMapper.selectList(any())).thenReturn(list);
        when(wearSessionMapper.selectCount(any())).thenReturn(0L);
        when(timelineRecordMapper.selectCount(any())).thenReturn(0L);
    }

    // ── 用例 ────────────────────────────────────────────────────

    @Test
    void start_date_unchanged_keeps_active_start_and_done_history() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 未修改开始日期（previous = stage.startDate = 09-01）
        service.reconcileAligners(stage(6, D0901), 3, D0901);

        // 当前副保持既有 startDate（currentDay 动态推算不被破坏）
        assertEquals(D0917, nodes.get(2).getStartDate(), "当前副 startDate 应保持既有值");
        assertEquals(D0923, nodes.get(2).getEndDate());
        // 前序 DONE 副真实历史日期不被重排
        assertEquals(D0901, nodes.get(0).getStartDate(), "前序 DONE 副日期不应被重排");
        assertEquals(D0908, nodes.get(0).getEndDate());
        assertEquals(D0913, nodes.get(1).getStartDate());
        assertEquals(D0916, nodes.get(1).getEndDate());
        // 后续副从当前副 endDate 顺延（无缝衔接）
        assertEquals(D0924, nodes.get(3).getStartDate());
        assertEquals(D1001, nodes.get(4).getStartDate());
        assertEquals(D1008, nodes.get(5).getStartDate());
    }

    @Test
    void start_date_unchanged_keeps_shifted_history_from_late_switches() {
        // 换副漂移场景：副1 实际戴了 12 天（09-01~09-12），副2 09-13~09-20，副3 09-21 起
        List<Aligner> nodes = existing();
        nodes.get(0).setEndDate(D0912);
        nodes.get(1).setStartDate(D0913);
        nodes.get(1).setEndDate(D0920);
        nodes.get(2).setStartDate(D0921);
        nodes.get(2).setEndDate(LocalDate.of(2026, 9, 27));
        nodes.get(3).setStartDate(LocalDate.of(2026, 9, 28));
        stubExisting(nodes);

        service.reconcileAligners(stage(6, D0901), 3, D0901);

        // 核心回归：真实佩戴历史不被「理论排期」覆盖（修复前副3 startDate 会被拉回 09-15 附近）
        assertEquals(D0912, nodes.get(0).getEndDate(), "漂移的 DONE 副 endDate 应保持实际值");
        assertEquals(D0921, nodes.get(2).getStartDate(), "当前副 startDate 不得被拉回阶段锚点的理论值");
        assertEquals(LocalDate.of(2026, 9, 28), nodes.get(3).getStartDate(), "后续副从当前副真实 endDate 顺延");
    }

    @Test
    void explicit_start_date_change_reanchors_active_and_future_only() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 显式把开始日期 09-01 → 09-10：当前副及之后从新日期重排，前序 DONE 副不动
        LocalDate newStart = LocalDate.of(2026, 9, 10);
        service.reconcileAligners(stage(6, newStart), 3, D0901);

        assertEquals(newStart, nodes.get(2).getStartDate(), "当前副从新开始日期起");
        assertEquals(LocalDate.of(2026, 9, 16), nodes.get(2).getEndDate());
        assertEquals(LocalDate.of(2026, 9, 17), nodes.get(3).getStartDate(), "后续副按新锚点顺延");
        // 前序 DONE 副保持真实历史
        assertEquals(D0901, nodes.get(0).getStartDate(), "改开始日期也不得改写已完成副的历史日期");
        assertEquals(D0908, nodes.get(0).getEndDate());
        assertEquals(D0913, nodes.get(1).getStartDate());
    }

    @Test
    void reverting_start_num_behind_recorded_aligners_is_rejected() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 副 4 有时光轴印记：起始副 5 → 3 会被回退为 FUTURE，必须拒绝
        when(timelineRecordMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reconcileAligners(stage(6, D0901), 3, D0901));
        assertTrue(ex.getMessage().contains("第4副"), "提示应包含首个被回退的副号，实际：" + ex.getMessage());
    }

    @Test
    void moving_start_num_forward_splits_open_session_to_new_aligner() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 佩戴中（open 会话挂在副 3）时把起始副 3 → 4（用户实际已手动换副，编辑校正）
        WearSession open = new WearSession();
        open.setId(900L);
        open.setUserId(1L);
        open.setAlignerId(3L);
        when(wearSessionMapper.selectOne(any())).thenReturn(open);

        service.reconcileAligners(stage(6, D0901), 4, D0901);

        // 旧会话被收口
        verify(wearSessionMapper).updateById(open);
        assertNotNull(open.getEndedAt(), "归属旧 ACTIVE 副的佩戴中会话应被收口");
        // 为新起始副另起会话
        ArgumentCaptor<WearSession> captor = ArgumentCaptor.forClass(WearSession.class);
        verify(wearSessionMapper).insert(captor.capture());
        WearSession fresh = captor.getValue();
        assertEquals(4L, fresh.getAlignerId(), "新会话应归属新起始副");
        assertEquals(WearSourceEnum.MANUAL.getCode(), fresh.getSource());
        assertNotNull(fresh.getStartedAt());
    }

    @Test
    void no_start_date_keeps_manually_scheduled_dates() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 阶段始终无开始日期（previous 也为 null）：不清空已手动排期的日期
        service.reconcileAligners(stage(6, null), 3, null);

        assertEquals(D0917, nodes.get(2).getStartDate(), "无开始日期阶段编辑不得清空既有排期");
        assertEquals(D0901, nodes.get(0).getStartDate());
        assertEquals(D0924, nodes.get(3).getStartDate());
    }

    @Test
    void clearing_start_date_only_wipes_active_and_future() {
        List<Aligner> nodes = existing();
        stubExisting(nodes);
        // 用户显式清空开始日期：当前副及之后清排期，前序 DONE 副历史保留
        service.reconcileAligners(stage(6, null), 3, D0901);

        assertNull(nodes.get(2).getStartDate(), "当前副排期应被清空");
        assertNull(nodes.get(5).getEndDate());
        assertEquals(D0901, nodes.get(0).getStartDate(), "前序 DONE 副历史日期保留");
        assertEquals(D0913, nodes.get(1).getStartDate());
    }
}
