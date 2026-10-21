package com.chiji.module.rubberband.vo;

import java.time.LocalDate;

/**
 * 橡皮筋打卡/取消结果 VO（POST /api/rubber-band/toggle，REQ-FB-006）。
 * <p>
 * 以落库状态返回（upsert 后回读，并发冲突时返回真实最终态）。
 *
 * @param recordDate 打卡日（服务端当日）
 * @param done       落库后的最终状态（true 已打卡 / false 已取消）
 */
public record RubberBandToggleVO(
        LocalDate recordDate,
        Boolean done
) {
}
