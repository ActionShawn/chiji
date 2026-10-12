package com.chiji.module.chew.vo;

/**
 * 「咬胶计时」页今日累计视图（#7）+ 首页静默态数据源。
 * <p>
 * {@code endedNetSec} 与日历/统计同源（仅已结束）；{@code netSec} 为展示值（含进行中实时净时长）。
 *
 * @param date         自然日 yyyy-MM-dd（Asia/Shanghai）
 * @param endedNetSec  当日已结束会话净时长合计（秒）
 * @param netSec       今日累计展示值（秒）= endedNetSec + 进行中会话实时净时长
 * @param count        当日已结束会话数（不含进行中）
 * @param reachedCount 当日已结束会话中 netSec >= targetSec 的条数
 * @param goal         每日建议累计（秒）= 1200；达标判定 displayedNetSec >= goal
 * @param active       进行中会话（无则 null）
 */
public record TodayChewVO(
        String date,
        Long endedNetSec,
        Long netSec,
        Integer count,
        Integer reachedCount,
        Integer goal,
        ChewSessionVO active
) {
}
