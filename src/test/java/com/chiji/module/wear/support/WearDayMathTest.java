package com.chiji.module.wear.support;

import com.chiji.entity.WearSession;
import com.chiji.module.wear.support.WearDayMath.DaySecs;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WearDayMath} 纯逻辑单测：跨天切分、今日封顶、MAKEUP/MANUAL 分桶、
 * 占位补零、0.1h 标签与百分比。不依赖 Spring 与数据库。
 */
class WearDayMathTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 6);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 8);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);

    private static WearSession session(LocalDateTime start, LocalDateTime end, String source) {
        WearSession s = new WearSession();
        s.setStartedAt(start);
        s.setEndedAt(end);
        s.setSource(source);
        return s;
    }

    @Test
    void distribute_cross_midnight_and_makeup_and_open_today() {
        // A：跨午夜手工打卡 09-07 22:00 -> 09-08 06:00
        WearSession a = session(LocalDateTime.of(2026, 9, 7, 22, 0),
                LocalDateTime.of(2026, 9, 8, 6, 0), "MANUAL");
        // B：校准补录 09-08 08:00 -> 10:00
        WearSession b = session(LocalDateTime.of(2026, 9, 8, 8, 0),
                LocalDateTime.of(2026, 9, 8, 10, 0), "MAKEUP");
        // C：佩戴中（未结束）09-08 09:30 -> 现在（12:00），当天只计到当下
        WearSession c = session(LocalDateTime.of(2026, 9, 8, 9, 30), null, "MANUAL");
        // D：昨天 20:00 开始、恰好在 09-08 00:00 结束，不得污染 09-08
        WearSession d = session(LocalDateTime.of(2026, 9, 7, 20, 0),
                LocalDateTime.of(2026, 9, 8, 0, 0), "MANUAL");

        Map<LocalDate, DaySecs> days = WearDayMath.distribute(List.of(a, b, c, d), D1, D3, NOW);

        assertEquals(3, days.size(), "from..to 每一日都要有占位键");

        DaySecs day1 = days.get(D1);
        assertEquals(0, day1.wear(), "无任何会话的日 wear=0");
        assertEquals(0, day1.manual());
        assertEquals(0, day1.makeup());

        DaySecs day2 = days.get(D2);
        assertEquals(21600, day2.wear(), "09-07 应为 A 的 22:00-24:00（2h=7200）+ D 的 20:00-24:00（4h=14400）");
        assertEquals(21600, day2.manual());
        assertEquals(0, day2.makeup());

        DaySecs day3 = days.get(D3);
        // A 归入 00:00-06:00(6h=21600) + B 校准(2h=7200) + C 截至当下 09:30-12:00(2.5h=9000)
        assertEquals(21600 + 7200 + 9000, day3.wear());
        assertEquals(21600 + 9000, day3.manual(), "D 恰在零点结束，不产生 09-08 任何秒数");
        assertEquals(7200, day3.makeup());
    }

    @Test
    void distribute_past_day_capped_to_natural_day_end() {
        // 会话跨「今天」之后才结束：过去日仍只按自然日截止 09-07 24:00 切分，
        // 今天（09-08）因当日尚未结束，按 now 封顶
        WearSession closedSpan = session(LocalDateTime.of(2026, 9, 7, 23, 0),
                LocalDateTime.of(2026, 9, 9, 1, 0), "MANUAL");

        Map<LocalDate, DaySecs> days = WearDayMath.distribute(List.of(closedSpan), D2, D3, NOW);

        DaySecs d2 = days.get(D2);
        assertEquals(3600, d2.wear(), "过去日 09-07 只计 23:00-24:00（1h）");
        DaySecs d3 = days.get(D3);
        assertEquals(12 * 3600, d3.wear(), "今天按 now 封顶，09-08 00:00-12:00（12h）");
    }

    @Test
    void dayOrZero_absent_date_returns_zero_day() {
        Map<LocalDate, DaySecs> days = Map.of(D3, new DaySecs(100, 100, 0));
        DaySecs missing = WearDayMath.dayOrZero(days, D1);
        assertEquals(0, missing.wear());
        assertEquals(0, missing.manual());
        assertEquals(0, missing.makeup());
        assertEquals(100, WearDayMath.dayOrZero(days, D3).wear());
    }

    @Test
    void tenthHoursLabel_floor_to_point1_hour() {
        assertEquals("0.0h", WearDayMath.tenthHoursLabel(0));
        assertEquals("0.0h", WearDayMath.tenthHoursLabel(359), "不足 6 分钟按 0.0h");
        assertEquals("0.1h", WearDayMath.tenthHoursLabel(360));
        assertEquals("1.0h", WearDayMath.tenthHoursLabel(3600));
        assertEquals("12.5h", WearDayMath.tenthHoursLabel(45120));
        assertEquals("20.0h", WearDayMath.tenthHoursLabel(72000));
    }

    @Test
    void percent_calculation() {
        assertEquals(0.0, WearDayMath.percent(0, 72000), 1e-9);
        assertEquals(50.0, WearDayMath.percent(36000, 72000), 1e-9);
        assertEquals(100.0, WearDayMath.percent(72000, 72000), 1e-9);
        assertEquals(0.0, WearDayMath.percent(0, 0), 1e-9, "goal<=0 返回 0");
        assertEquals(0.0, WearDayMath.percent(123, -1), 1e-9);
    }

    @Test
    void clip_closed_session_inside_day() {
        WearSession s = session(LocalDateTime.of(2026, 9, 8, 10, 0),
                LocalDateTime.of(2026, 9, 8, 11, 0), "MANUAL");
        LocalDateTime[] r = WearDayMath.clip(s, D3, LocalDateTime.of(2026, 9, 8, 12, 0));
        assertEquals(LocalDateTime.of(2026, 9, 8, 10, 0), r[0]);
        assertEquals(LocalDateTime.of(2026, 9, 8, 11, 0), r[1]);
    }

    @Test
    void clip_open_session_today_marks_still_wearing() {
        WearSession open = session(LocalDateTime.of(2026, 9, 8, 9, 30), null, "MANUAL");
        LocalDateTime[] r = WearDayMath.clip(open, D3, NOW);
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 30), r[0]);
        assertNull(r[1], "佩戴中会话 today 视图中 end 为 null 供前端渲染「佩戴中」");
    }

    @Test
    void clip_session_ended_before_day_start_gives_null() {
        WearSession s = session(LocalDateTime.of(2026, 9, 7, 22, 0),
                LocalDateTime.of(2026, 9, 8, 0, 0), "MANUAL");
        LocalDateTime[] r = WearDayMath.clip(s, D3, NOW);
        assertTrue(r == null || r.length == 0, "零点前已结束的会话不构成 09-08 的可见段");
    }
}
