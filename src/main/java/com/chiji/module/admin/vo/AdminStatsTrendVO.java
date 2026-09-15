package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端看板近 7 天趋势 VO。
 * <p>
 * 一次返回全部指标序列，前端本地切换趋势线不发请求；今日由明细实时聚合。
 */
@Data
@Builder
public class AdminStatsTrendVO {

    /** 7 天数据点（含今日） */
    private List<Point> points;

    /**
     * 单日数据点（缺数据日补 0）。
     */
    @Data
    @Builder
    public static class Point {

        /** 日期 */
        private LocalDate date;

        /** 新增用户数 */
        private long newUsers;

        /** 登录用户数（去重） */
        private long loginUsers;

        /** 使用时长（秒） */
        private long usageSec;

        /** 使用用户数（去重） */
        private long usageUsers;

        /** 接口调用数 */
        private long apiCalls;

        /** 新增记录数 */
        private long newRecords;

        /** 新增图片数 */
        private long newImages;
    }
}
