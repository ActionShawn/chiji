package com.chiji.module.wear.vo;

/**
 * 今日会话时间线节点（跨昨日延续会话在今日区间内展示）。
 *
 * @param id         会话 id
 * @param source     MANUAL / MAKEUP
 * @param makeupFor  校准补录目标日期 yyyy-MM-dd（补录段可见）
 * @param startTime  会话在今日区间的起始 epoch 毫秒
 * @param endTime    会话在今日区间的结束 epoch 毫秒（佩戴中为 null）
 * @param durationSec 今日区间内归属秒
 * @param wearing    是否佩戴中
 */
public record TodaySessionVO(
        Long id,
        String source,
        String makeupFor,
        Long startTime,
        Long endTime,
        Long durationSec,
        Boolean wearing
) {
}
