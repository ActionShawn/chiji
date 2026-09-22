package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理端运维看板接口趋势 VO。
 * <p>
 * 粒度随区间自适应：当天=小时（24 点）、近 7/30 天=日、全部=月；
 * 调用次数与最大耗时两条折线同源返回，前端本地切换。
 */
@Data
@Builder
public class ApiTrendVO {

    /** 区间：day / week / month / all */
    private String range;

    /** 粒度：hour / day / month */
    private String granularity;

    /** 数据点（缺数据分组补 0） */
    private List<Point> points;

    /**
     * 单点数据。
     */
    @Data
    @Builder
    public static class Point {

        /** 标签（小时「09」/ 日期「09-20」/ 月份「2026-09」） */
        private String label;

        /** 调用数 */
        private long cnt;

        /** 失败数 */
        private long errCnt;

        /** 最大耗时 ms */
        private long maxMs;
    }
}
