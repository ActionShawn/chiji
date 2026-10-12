package com.chiji.module.chew.support;

import com.chiji.entity.ChewSession;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 咬胶时长派生工具。
 * <p>
 * 净时长 = {@code (endedAt | pausedAt | now) − startedAt − pauseSec}，由服务端派生、不落库：
 * <ul>
 *   <li>已结束（{@code endedAt != null}）：冻结为 {@code endedAt − startedAt − pauseSec}；</li>
 *   <li>暂停中（{@code endedAt == null && pausedAt != null}）：冻结为 {@code pausedAt − startedAt − pauseSec}；</li>
 *   <li>运行中（两者均 null）：{@code now − startedAt − pauseSec}，随服务端 now 实时递增。</li>
 * </ul>
 * 结果不小于 0（防御脏数据/时钟异常）。
 */
public final class ChewMath {

    private ChewMath() {
    }

    /** 两时刻之间的整秒差（可为 0；不足 1 秒向下取整，与 MySQL TIMESTAMPDIFF(SECOND) 一致）。 */
    public static int secondsBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return (int) ChronoUnit.SECONDS.between(from, to);
    }

    /**
     * 派生会话净时长（秒）。
     *
     * @param s   会话
     * @param now 服务端当前时刻（业务时区）
     * @return 净时长（秒，≥ 0）
     */
    public static long netSec(ChewSession s, LocalDateTime now) {
        if (s == null || s.getStartedAt() == null) {
            return 0L;
        }
        LocalDateTime end;
        if (s.getEndedAt() != null) {
            end = s.getEndedAt();
        } else if (s.getPausedAt() != null) {
            end = s.getPausedAt();
        } else {
            end = now;
        }
        long net = (long) secondsBetween(s.getStartedAt(), end) - nz(s.getPauseSec());
        return Math.max(0L, net);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
