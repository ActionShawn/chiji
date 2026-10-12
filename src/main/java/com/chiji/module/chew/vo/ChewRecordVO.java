package com.chiji.module.chew.vo;

/**
 * 单条咬胶记录（日历页某日明细）。
 *
 * @param id           会话 ID
 * @param startedAt    开始时刻 epoch 毫秒
 * @param endedAt      结束时刻 epoch 毫秒
 * @param netSec       净时长（秒）
 * @param alignerLabel 归属标签「第X副 · 第Y天」；无归属为 ""
 */
public record ChewRecordVO(
        Long id,
        Long startedAt,
        Long endedAt,
        Long netSec,
        String alignerLabel
) {
}
