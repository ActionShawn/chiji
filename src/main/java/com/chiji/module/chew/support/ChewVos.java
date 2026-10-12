package com.chiji.module.chew.support;

import com.chiji.entity.ChewSession;
import com.chiji.module.chew.vo.ChewSessionVO;

import java.time.LocalDateTime;

/**
 * 咬胶会话 → VO 装配工具。
 * <p>
 * 统一派生运行态（{@code running}/{@code paused}/{@code reached}）与净时长，避免多处重复计算。
 * 时刻字段返回 epoch 毫秒，时长字段返回秒。
 */
public final class ChewVos {

    private ChewVos() {
    }

    /**
     * 装配会话视图。
     *
     * @param s            会话（可空）
     * @param now          服务端当前时刻（业务时区，用于派生进行中净时长）
     * @param alignerLabel 归属标签（可空，null → 空串）
     * @return 会话视图；{@code s} 为空时返回 null
     */
    public static ChewSessionVO toVO(ChewSession s, LocalDateTime now, String alignerLabel) {
        if (s == null) {
            return null;
        }
        boolean ended = s.getEndedAt() != null;
        boolean paused = !ended && s.getPausedAt() != null;
        boolean running = !ended && s.getPausedAt() == null;
        long net = ChewMath.netSec(s, now);
        int target = targetOf(s);
        return new ChewSessionVO(
                s.getId(),
                s.getStageId(),
                s.getAlignerId(),
                alignerLabel == null ? "" : alignerLabel,
                millis(s.getStartedAt()),
                millis(s.getEndedAt()),
                nz(s.getPauseSec()),
                net,
                running,
                paused,
                target,
                net >= target
        );
    }

    /** 会话目标时长（秒）：缺失/非法回退默认档 {@link ChewConstants#DEFAULT_TARGET_SEC}。 */
    public static int targetOf(ChewSession s) {
        if (s == null || s.getTargetSec() == null || s.getTargetSec() <= 0) {
            return ChewConstants.DEFAULT_TARGET_SEC;
        }
        return s.getTargetSec();
    }

    private static Long millis(LocalDateTime t) {
        return t == null ? null : ChewTimes.toEpochMillis(t);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
