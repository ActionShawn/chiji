package com.chiji.module.chew.vo;

import java.util.List;

/**
 * 咬胶统计视图（#8，P1；一律「已结束」口径，不含进行中）。
 *
 * @param range               统计口径：LAST_7 / CURRENT_MONTH / LAST_30 / YEAR
 * @param startDate           区间起始 yyyy-MM-dd
 * @param endDate             区间结束 yyyy-MM-dd（含今天）
 * @param goal                区间适用每日建议（秒）= 1200
 * @param totalNetSec         区间已结束净时长合计（秒）
 * @param sessionCount        区间已结束会话数
 * @param activeDayCount      区间有记录的天数
 * @param reachedDayCount     区间日净时长 >= goal 的天数
 * @param avgNetSecPerActiveDay totalNetSec / activeDayCount，四舍五入；无记录为 0
 * @param days                逐日序列（供图表）
 */
public record ChewStatsVO(
        String range,
        String startDate,
        String endDate,
        Integer goal,
        Long totalNetSec,
        Integer sessionCount,
        Integer activeDayCount,
        Integer reachedDayCount,
        Long avgNetSecPerActiveDay,
        List<StatsDayVO> days
) {

    /**
     * 统计逐日节点。
     *
     * @param date   自然日 yyyy-MM-dd
     * @param netSec 当日已结束净时长（秒）
     * @param count  当日已结束会话数
     */
    public record StatsDayVO(String date, Long netSec, Integer count) {
    }
}
