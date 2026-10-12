package com.chiji.module.chew.service.impl;

import com.chiji.common.core.dto.DayValueRow;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.entity.Aligner;
import com.chiji.entity.ChewSession;
import com.chiji.module.chew.mapper.ChewSessionMapper;
import com.chiji.module.chew.support.ChewConstants;
import com.chiji.module.chew.support.ChewTimes;
import com.chiji.module.chew.vo.ChewDayDetailVO;
import com.chiji.module.chew.vo.ChewDayVO;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.ChewStatsVO;
import com.chiji.module.chew.vo.TodayChewVO;
import com.chiji.module.stage.mapper.AlignerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 咬胶只读查询口径测试。
 * <p>
 * 覆盖 today（已结束合计 + 进行中实时并入 + reachedCount）、calendar（整月逐日 0 填充平行数组）、
 * day（仅已结束 + 归属标签）、stats（range 规整/区间/聚合/非法值）、active（无进行中返回 null）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChewQueryServiceImplTest {

    private static final Long USER_ID = 10001L;

    @Mock
    private ChewSessionMapper chewSessionMapper;
    @Mock
    private AlignerMapper alignerMapper;

    private ChewQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChewQueryServiceImpl(chewSessionMapper, alignerMapper);
    }

    private static DayValueRow row(LocalDate date, Long value) {
        DayValueRow r = new DayValueRow();
        r.setDate(date);
        r.setValue(value);
        return r;
    }

    private static ChewSession session(Long id, LocalDateTime start, LocalDateTime end,
                                       LocalDateTime paused, Integer pauseSec, Integer targetSec) {
        ChewSession s = new ChewSession();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setStartedAt(start);
        s.setEndedAt(end);
        s.setPausedAt(paused);
        s.setPauseSec(pauseSec);
        s.setTargetSec(targetSec);
        return s;
    }

    // ── active ─────────────────────────────────────────────────────

    @Test
    void active_null_when_no_running() {
        when(chewSessionMapper.selectOne(any())).thenReturn(null);
        assertNull(service.active(USER_ID));
    }

    @Test
    void active_returns_running_vo() {
        ChewSession running = session(5L, ChewTimes.now().minusSeconds(60), null, null, 0, 600);
        when(chewSessionMapper.selectOne(any())).thenReturn(running);

        ChewSessionVO vo = service.active(USER_ID);

        assertNotNull(vo);
        assertEquals(5L, vo.id());
        assertTrue(vo.running());
    }

    // ── today ──────────────────────────────────────────────────────

    @Test
    void today_merges_ended_and_running() {
        LocalDate today = ChewTimes.today();
        ChewSession ended = session(1L, ChewTimes.now().minusSeconds(700), ChewTimes.now(), null, 0, 600);
        ChewSession running = session(2L, ChewTimes.now().minusSeconds(60), null, null, 0, 600);
        when(chewSessionMapper.sumNetSecByDay(any(), any(), any())).thenReturn(List.of(row(today, 700L)));
        when(chewSessionMapper.countByDay(any(), any(), any())).thenReturn(List.of(row(today, 1L)));
        when(chewSessionMapper.listOfDay(any(), any(), any())).thenReturn(List.of(ended));
        when(chewSessionMapper.selectOne(any())).thenReturn(running);

        TodayChewVO vo = service.today(USER_ID);

        assertEquals(today.toString(), vo.date());
        assertEquals(700L, vo.endedNetSec());
        assertEquals(1, vo.count());
        assertEquals(1, vo.reachedCount());
        assertEquals(ChewConstants.GOAL_SEC, vo.goal());
        assertNotNull(vo.active());
        assertTrue(vo.active().running());
        // 展示值 = 已结束 700 + 进行中 ~60
        assertTrue(vo.netSec() >= 760L && vo.netSec() <= 762L);
    }

    @Test
    void today_empty_when_no_data() {
        when(chewSessionMapper.sumNetSecByDay(any(), any(), any())).thenReturn(List.of());
        when(chewSessionMapper.countByDay(any(), any(), any())).thenReturn(List.of());
        when(chewSessionMapper.listOfDay(any(), any(), any())).thenReturn(List.of());
        when(chewSessionMapper.selectOne(any())).thenReturn(null);

        TodayChewVO vo = service.today(USER_ID);

        assertEquals(0L, vo.endedNetSec());
        assertEquals(0L, vo.netSec());
        assertEquals(0, vo.count());
        assertEquals(0, vo.reachedCount());
        assertEquals(ChewConstants.GOAL_SEC, vo.goal());
        assertNull(vo.active());
    }

    // ── calendar ───────────────────────────────────────────────────

    @Test
    void calendar_fills_whole_month_parallel_arrays() {
        YearMonth month = YearMonth.of(2026, 2);
        when(chewSessionMapper.sumNetSecByDay(any(), any(), any()))
                .thenReturn(List.of(row(LocalDate.of(2026, 2, 5), 300L)));
        when(chewSessionMapper.countByDay(any(), any(), any()))
                .thenReturn(List.of(row(LocalDate.of(2026, 2, 5), 2L)));

        ChewDayVO vo = service.calendar(USER_ID, month);

        assertEquals("2026-02", vo.month());
        assertEquals(ChewConstants.GOAL_SEC, vo.goal());
        assertEquals(28, vo.days().size());
        assertEquals("2026-02-01", vo.days().get(0));
        assertEquals("2026-02-28", vo.days().get(27));
        assertEquals(0L, vo.netSec().get(0));
        assertEquals(300L, vo.netSec().get(4));
        assertEquals(2, vo.count().get(4));
        assertEquals(0, vo.count().get(0));
    }

    // ── day ────────────────────────────────────────────────────────

    @Test
    void day_filters_running_and_builds_label() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        ChewSession ended = session(1L, date.atTime(8, 0), date.atTime(8, 10), null, 0, 600);
        ended.setAlignerId(9L);
        ChewSession running = session(2L, date.atTime(9, 0), null, null, 0, 600);
        when(chewSessionMapper.listOfDay(any(), any(), any())).thenReturn(List.of(ended, running));
        Aligner aligner = new Aligner();
        aligner.setId(9L);
        aligner.setNum(3);
        aligner.setStartDate(LocalDate.of(2026, 3, 6));
        when(alignerMapper.selectList(any())).thenReturn(List.of(aligner));

        ChewDayDetailVO vo = service.day(USER_ID, date);

        assertEquals(date.toString(), vo.date());
        assertEquals(600L, vo.netSec());
        assertEquals(1, vo.count());
        assertEquals(1, vo.sessions().size());
        assertEquals(600L, vo.sessions().get(0).netSec());
        // 第 3 副，开始日 03-06 → 会话日 03-10 为第 5 天
        assertEquals("第3副 · 第5天", vo.sessions().get(0).alignerLabel());
    }

    @Test
    void day_without_records_returns_empty_sessions() {
        LocalDate date = LocalDate.of(2026, 3, 11);
        when(chewSessionMapper.listOfDay(any(), any(), any())).thenReturn(List.of());

        ChewDayDetailVO vo = service.day(USER_ID, date);

        assertEquals(0L, vo.netSec());
        assertEquals(0, vo.count());
        assertTrue(vo.sessions().isEmpty());
    }

    // ── stats ──────────────────────────────────────────────────────

    @Test
    void stats_default_last7_all_zero() {
        when(chewSessionMapper.sumNetSecByDay(any(), any(), any())).thenReturn(List.of());
        when(chewSessionMapper.countByDay(any(), any(), any())).thenReturn(List.of());

        ChewStatsVO vo = service.stats(USER_ID, null);

        assertEquals(ChewConstants.RANGE_LAST_7, vo.range());
        assertEquals(7, vo.days().size());
        assertEquals(0L, vo.totalNetSec());
        assertEquals(0, vo.sessionCount());
        assertEquals(0, vo.activeDayCount());
        assertEquals(0, vo.reachedDayCount());
        assertEquals(0L, vo.avgNetSecPerActiveDay());
        assertEquals(ChewTimes.today().toString(), vo.endDate());
        assertEquals(ChewTimes.today().minusDays(6).toString(), vo.startDate());
    }

    @Test
    void stats_range_normalized_and_aggregated() {
        LocalDate today = ChewTimes.today();
        when(chewSessionMapper.sumNetSecByDay(any(), any(), any())).thenReturn(List.of(row(today, 1500L)));
        when(chewSessionMapper.countByDay(any(), any(), any())).thenReturn(List.of(row(today, 2L)));

        ChewStatsVO vo = service.stats(USER_ID, "last_30");

        assertEquals(ChewConstants.RANGE_LAST_30, vo.range());
        assertEquals(30, vo.days().size());
        assertEquals(1500L, vo.totalNetSec());
        assertEquals(2, vo.sessionCount());
        assertEquals(1, vo.activeDayCount());
        assertEquals(1, vo.reachedDayCount());
        assertEquals(1500L, vo.avgNetSecPerActiveDay());
        assertEquals(today.toString(), vo.endDate());
        assertEquals(today.minusDays(29).toString(), vo.startDate());
    }

    @Test
    void stats_invalid_range_throws() {
        assertThrows(BusinessException.class, () -> service.stats(USER_ID, "WEEK"));
    }
}
