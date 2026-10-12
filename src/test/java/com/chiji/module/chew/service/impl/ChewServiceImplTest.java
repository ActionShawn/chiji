package com.chiji.module.chew.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.ChewSession;
import com.chiji.module.chew.dto.ChewStartRequest;
import com.chiji.module.chew.dto.ChewStateChangeRequest;
import com.chiji.module.chew.mapper.ChewSessionMapper;
import com.chiji.module.chew.service.ChewQueryService;
import com.chiji.module.chew.support.ChewConstants;
import com.chiji.module.chew.support.ChewSourceEnum;
import com.chiji.module.chew.support.ChewTimes;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.TodayChewVO;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.service.AlignerService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 咬胶会话状态机测试。
 * <p>
 * 覆盖 start 幂等/快照/唯一键冲突回读、pause/resume/restart/finish 的状态分支与幂等不变式、
 * finish 误触（净时长 &lt; 5s）逻辑删除、discard 归属与状态校验、targetSec 越界。
 * <p>
 * 关键实现约定：状态变更走 {@code update(null, LambdaUpdateWrapper)} 显式置空，故断言以
 * {@code update(isNull(), any())} 与返回 VO 的派生字段为准。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChewServiceImplTest {

    private static final Long USER_ID = 10001L;
    private static final Long SESSION_ID = 1L;

    @Mock
    private ChewSessionMapper chewSessionMapper;
    @Mock
    private AlignerService alignerService;
    @Mock
    private AlignerMapper alignerMapper;
    @Mock
    private ChewQueryService chewQueryService;

    private ChewServiceImpl service;

    /**
     * 纯 Mockito 环境无 MyBatis-Plus 的 TableInfo 初始化，{@code LambdaUpdateWrapper.set(...)}
     * 解析列名时会抛 {@code can not find lambda cache for this entity}。这里手动注册一次
     * 实体元信息（initTableInfo 内部会同步安装 LambdaUtils 缓存），与生产运行时行为一致。
     */
    @BeforeAll
    static void initMybatisPlusMeta() {
        MapperBuilderAssistant assistant = new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChewSession.class);
    }

    @BeforeEach
    void setUp() {
        service = new ChewServiceImpl(chewSessionMapper, alignerService, alignerMapper, chewQueryService);
    }

    private static ChewSession session(Long id, Long userId, LocalDateTime start, LocalDateTime end,
                                       LocalDateTime paused, Integer pauseSec, Integer targetSec) {
        ChewSession s = new ChewSession();
        s.setId(id);
        s.setUserId(userId);
        s.setStartedAt(start);
        s.setEndedAt(end);
        s.setPausedAt(paused);
        s.setPauseSec(pauseSec);
        s.setTargetSec(targetSec);
        return s;
    }

    // ── start ──────────────────────────────────────────────────────

    @Test
    void start_returns_existing_running_idempotently() {
        ChewSession running = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(30), null, null, 0, 600);
        when(chewSessionMapper.selectOne(any())).thenReturn(running);

        ChewSessionVO vo = service.start(USER_ID, new ChewStartRequest(600, "c1", null));

        assertEquals(SESSION_ID, vo.id());
        assertTrue(vo.running());
        verify(chewSessionMapper, never()).insertIgnore(any());
    }

    @Test
    void start_inserts_new_session_with_audit_fields() {
        when(chewSessionMapper.selectOne(any())).thenReturn(null);
        when(alignerService.findActiveAligner(eq(USER_ID), isNull())).thenReturn(null);
        when(chewSessionMapper.insertIgnore(any())).thenReturn(1);

        ChewSessionVO vo = service.start(USER_ID, new ChewStartRequest(null, "c1", null));

        ArgumentCaptor<ChewSession> captor = ArgumentCaptor.forClass(ChewSession.class);
        verify(chewSessionMapper).insertIgnore(captor.capture());
        ChewSession saved = captor.getValue();
        assertNotNull(saved.getId(), "自定义 @Insert 不填充雪花 id，service 必须显式赋值");
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(ChewConstants.DEFAULT_TARGET_SEC, saved.getTargetSec());
        assertEquals(ChewSourceEnum.REALTIME.getCode(), saved.getSource());
        assertEquals(0, saved.getEdited());
        assertEquals(0, saved.getDeleted());
        assertNotNull(saved.getStartedAt());
        assertNotNull(saved.getCreatedAt());
        assertEquals("c1", saved.getClientSessionId());
        assertEquals(ChewConstants.DEFAULT_TARGET_SEC, vo.targetSec());
        assertTrue(vo.running());
    }

    @Test
    void start_snapshots_active_stage_and_aligner() {
        when(chewSessionMapper.selectOne(any())).thenReturn(null);
        Aligner active = new Aligner();
        active.setId(9L);
        active.setStageId(7L);
        active.setNum(3);
        when(alignerService.findActiveAligner(eq(USER_ID), eq("CLEAR_SINGLE"))).thenReturn(active);
        when(chewSessionMapper.insertIgnore(any())).thenReturn(1);

        service.start(USER_ID, new ChewStartRequest(600, "c1", "CLEAR_SINGLE"));

        ArgumentCaptor<ChewSession> captor = ArgumentCaptor.forClass(ChewSession.class);
        verify(chewSessionMapper).insertIgnore(captor.capture());
        assertEquals(7L, captor.getValue().getStageId());
        assertEquals(9L, captor.getValue().getAlignerId());
    }

    @Test
    void start_conflict_rereads_running_session() {
        ChewSession running = session(2L, USER_ID, ChewTimes.now().minusSeconds(10), null, null, 0, 600);
        when(chewSessionMapper.selectOne(any())).thenReturn(null, running);
        when(alignerService.findActiveAligner(any(), any())).thenReturn(null);
        when(chewSessionMapper.insertIgnore(any())).thenReturn(0);

        ChewSessionVO vo = service.start(USER_ID, new ChewStartRequest(600, "c1", null));

        assertEquals(2L, vo.id());
    }

    @Test
    void start_conflict_without_running_throws_already_running() {
        when(chewSessionMapper.selectOne(any())).thenReturn(null);
        when(alignerService.findActiveAligner(any(), any())).thenReturn(null);
        when(chewSessionMapper.insertIgnore(any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.start(USER_ID, new ChewStartRequest(600, "c1", null)));
        assertEquals(ErrorCode.CHEW_SESSION_ALREADY_RUNNING.getCode(), ex.getCode());
    }

    @Test
    void start_target_out_of_range_throws_param_invalid() {
        when(chewSessionMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.start(USER_ID, new ChewStartRequest(10, "c1", null)));
        assertEquals(ErrorCode.CHEW_PARAM_INVALID.getCode(), ex.getCode());
        verify(chewSessionMapper, never()).insertIgnore(any());
    }

    // ── pause ──────────────────────────────────────────────────────

    @Test
    void pause_ended_throws_forbidden() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), ChewTimes.now(), null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pause(USER_ID, SESSION_ID, null));
        assertEquals(ErrorCode.CHEW_SESSION_FORBIDDEN.getCode(), ex.getCode());
        verify(chewSessionMapper, never()).update(any(), any());
    }

    @Test
    void pause_already_paused_is_idempotent() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), null,
                ChewTimes.now().minusSeconds(30), 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.pause(USER_ID, SESSION_ID, null);

        assertTrue(vo.paused());
        verify(chewSessionMapper, never()).update(any(), any());
    }

    @Test
    void pause_sets_paused_at_and_persists() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.pause(USER_ID, SESSION_ID, null);

        assertTrue(vo.paused());
        assertFalse(vo.running());
        verify(chewSessionMapper).update(isNull(), any());
    }

    @Test
    void pause_applies_target_adjustment() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.pause(USER_ID, SESSION_ID, new ChewStateChangeRequest(660));

        assertEquals(660, vo.targetSec());
    }

    @Test
    void pause_invalid_target_throws() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        assertThrows(BusinessException.class,
                () -> service.pause(USER_ID, SESSION_ID, new ChewStateChangeRequest(1)));
    }

    // ── resume ─────────────────────────────────────────────────────

    @Test
    void resume_settles_pause_seconds() {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = session(SESSION_ID, USER_ID, now.minusSeconds(600), null, now.minusSeconds(30), 10, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.resume(USER_ID, SESSION_ID, null);

        // pauseSec = 10（已结算）+ ~30（暂停段）= ~40
        assertTrue(vo.pauseSec() >= 40 && vo.pauseSec() <= 42, "暂停段应结算进 pauseSec");
        assertTrue(vo.running());
        assertFalse(vo.paused());
        verify(chewSessionMapper).update(isNull(), any());
    }

    @Test
    void resume_when_not_paused_is_idempotent() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.resume(USER_ID, SESSION_ID, null);

        assertTrue(vo.running());
        verify(chewSessionMapper, never()).update(any(), any());
    }

    @Test
    void resume_ended_throws_forbidden() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), ChewTimes.now(), null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        assertThrows(BusinessException.class, () -> service.resume(USER_ID, SESSION_ID, null));
    }

    // ── restart ────────────────────────────────────────────────────

    @Test
    void restart_resets_start_and_pause_keeps_snapshot() {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = session(SESSION_ID, USER_ID, now.minusSeconds(5000), null, now.minusSeconds(10), 300, 600);
        s.setStageId(7L);
        s.setAlignerId(9L);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.restart(USER_ID, SESSION_ID, null);

        assertEquals(0, vo.pauseSec());
        assertTrue(vo.running());
        assertEquals(7L, vo.stageId());
        assertEquals(9L, vo.alignerId());
        assertTrue(vo.netSec() < ChewConstants.MIN_VALID_NET_SEC, "重来后净时长应从零起算");
        verify(chewSessionMapper).update(isNull(), any());
    }

    @Test
    void restart_ended_throws_forbidden() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), ChewTimes.now(), null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        assertThrows(BusinessException.class, () -> service.restart(USER_ID, SESSION_ID, null));
    }

    // ── finish ─────────────────────────────────────────────────────

    @Test
    void finish_ended_is_idempotent() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600),
                ChewTimes.now().minusSeconds(10), null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.finish(USER_ID, SESSION_ID, null);

        assertFalse(vo.running());
        assertNotNull(vo.endedAt());
        verify(chewSessionMapper, never()).update(any(), any());
        verify(chewSessionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void finish_under_min_valid_discards_and_returns_null() {
        // 净时长 < 5s 视为误触：逻辑删除且返回 null
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(2), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.finish(USER_ID, SESSION_ID, null);

        assertNull(vo);
        verify(chewSessionMapper).deleteById(SESSION_ID);
        verify(chewSessionMapper, never()).update(any(), any());
    }

    @Test
    void finish_valid_persists_ended_at() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.finish(USER_ID, SESSION_ID, null);

        assertNotNull(vo);
        assertNotNull(vo.endedAt());
        assertFalse(vo.running());
        assertTrue(vo.netSec() >= 600L);
        verify(chewSessionMapper).update(isNull(), any());
        verify(chewSessionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void finish_settles_pending_pause_before_end() {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = session(SESSION_ID, USER_ID, now.minusSeconds(1000), null, now.minusSeconds(100), 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        ChewSessionVO vo = service.finish(USER_ID, SESSION_ID, null);

        assertNotNull(vo);
        assertNotNull(vo.endedAt());
        assertTrue(vo.pauseSec() >= 100, "结束前应结算未结暂停段");
        assertTrue(vo.netSec() >= 890L && vo.netSec() <= 910L);
    }

    // ── discard（#12 DELETE） ───────────────────────────────────────

    @Test
    void discard_not_found_throws_not_found() {
        when(chewSessionMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.discard(USER_ID, 99L));
        assertEquals(ErrorCode.CHEW_SESSION_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void discard_other_user_throws_not_found() {
        ChewSession s = session(SESSION_ID, 88888L, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.discard(USER_ID, SESSION_ID));
        assertEquals(ErrorCode.CHEW_SESSION_NOT_FOUND.getCode(), ex.getCode());
        verify(chewSessionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void discard_ended_throws_forbidden() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(600), ChewTimes.now(), null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.discard(USER_ID, SESSION_ID));
        assertEquals(ErrorCode.CHEW_SESSION_FORBIDDEN.getCode(), ex.getCode());
        verify(chewSessionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void discard_success_deletes_and_returns_today() {
        ChewSession s = session(SESSION_ID, USER_ID, ChewTimes.now().minusSeconds(100), null, null, 0, 600);
        when(chewSessionMapper.selectById(SESSION_ID)).thenReturn(s);
        TodayChewVO today = new TodayChewVO("2026-10-12", 0L, 0L, 0, 0, ChewConstants.GOAL_SEC, null);
        when(chewQueryService.today(USER_ID)).thenReturn(today);

        TodayChewVO vo = service.discard(USER_ID, SESSION_ID);

        assertSame(today, vo);
        verify(chewSessionMapper).deleteById(SESSION_ID);
    }
}
