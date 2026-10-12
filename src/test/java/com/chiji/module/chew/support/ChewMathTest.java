package com.chiji.module.chew.support;

import com.chiji.entity.ChewSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 咬胶净时长派生口径测试。
 * <p>
 * 覆盖 {@link ChewMath#netSec(ChewSession, LocalDateTime)} 的三态（已结束/暂停中/运行中）冻结与实时语义、
 * 暂停秒扣减、null 容错、脏数据归零，以及 {@link ChewMath#secondsBetween} 的整秒向下取整口径
 * （与 MySQL {@code TIMESTAMPDIFF(SECOND)} 对齐）。
 */
class ChewMathTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 12, 9, 0, 0);

    private static ChewSession session(LocalDateTime start, LocalDateTime end, LocalDateTime paused, Integer pauseSec) {
        ChewSession s = new ChewSession();
        s.setStartedAt(start);
        s.setEndedAt(end);
        s.setPausedAt(paused);
        s.setPauseSec(pauseSec);
        return s;
    }

    @Test
    void seconds_between_null_returns_zero() {
        assertEquals(0, ChewMath.secondsBetween(null, BASE));
        assertEquals(0, ChewMath.secondsBetween(BASE, null));
        assertEquals(0, ChewMath.secondsBetween(null, null));
    }

    @Test
    void seconds_between_truncates_to_whole_seconds() {
        assertEquals(5, ChewMath.secondsBetween(BASE, BASE.plusSeconds(5)));
        assertEquals(5, ChewMath.secondsBetween(BASE, BASE.plusSeconds(5).plusNanos(900_000_000)));
        assertEquals(0, ChewMath.secondsBetween(BASE, BASE.plusNanos(999_000_000)));
    }

    @Test
    void net_sec_null_or_without_start_returns_zero() {
        assertEquals(0L, ChewMath.netSec(null, BASE));
        assertEquals(0L, ChewMath.netSec(session(null, null, null, 0), BASE));
    }

    @Test
    void net_sec_ended_is_frozen_regardless_of_now() {
        // 已结束：endedAt − startedAt − pauseSec，与 now 无关
        ChewSession s = session(BASE, BASE.plusSeconds(600), null, 30);
        assertEquals(570L, ChewMath.netSec(s, BASE.plusHours(5)));
    }

    @Test
    void net_sec_paused_freezes_at_paused_at() {
        // 暂停中：pausedAt − startedAt − pauseSec，随 now 不变（暂停冻结）
        ChewSession s = session(BASE, null, BASE.plusSeconds(200), 10);
        assertEquals(190L, ChewMath.netSec(s, BASE.plusHours(3)));
    }

    @Test
    void net_sec_running_grows_with_now() {
        // 运行中：now − startedAt − pauseSec
        ChewSession s = session(BASE, null, null, 0);
        assertEquals(120L, ChewMath.netSec(s, BASE.plusSeconds(120)));
        assertEquals(300L, ChewMath.netSec(s, BASE.plusSeconds(300)));
    }

    @Test
    void net_sec_null_pause_treated_as_zero() {
        ChewSession s = session(BASE, BASE.plusSeconds(90), null, null);
        assertEquals(90L, ChewMath.netSec(s, BASE));
    }

    @Test
    void net_sec_clamped_at_zero_on_dirty_data() {
        // 脏数据（pauseSec 大于区间）：归零，不出现负数
        ChewSession s = session(BASE, null, null, 9999);
        assertEquals(0L, ChewMath.netSec(s, BASE.plusSeconds(100)));
    }
}
