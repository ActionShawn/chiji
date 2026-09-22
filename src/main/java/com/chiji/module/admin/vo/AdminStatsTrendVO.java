package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理端运营看板趋势 VO。
 * <p>
 * 粒度随区间自适应：当天=小时（24 点）、近 7/30 天=日、全部=月；
 * 一次返回全部指标序列，前端本地切换趋势线不发请求。
 */
@Data
@Builder
public class AdminStatsTrendVO {

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

        /** 新增用户数 */
        private long newUsers;

        /** 使用人数（区间内去重） */
        private long usageUsers;

        /** 使用时长（秒） */
        private long usageSec;

        /** 接口调用数 */
        private long apiCalls;

        /** 新增记录数 */
        private long newRecords;

        /** 新增照片数 */
        private long newImages;
    }
}
