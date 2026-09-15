package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端看板指标总览 VO。
 * <p>
 * 区间口径（period 决定）：今日 / 近 7 天 / 近 30 天，实时聚合明细表，
 * 今日数据天然鲜活（api_calls 含 ≤5 分钟刷盘延迟）。
 */
@Data
@Builder
public class AdminStatsOverviewVO {

    /** 周期：day / week / month */
    private String period;

    /** 新增用户数 */
    private long newUsers;

    /** 登录用户数（去重） */
    private long loginUsers;

    /** 使用时长总和（秒） */
    private long usageSec;

    /** 使用用户数（去重） */
    private long usageUsers;

    /** 接口调用总数 */
    private long apiCalls;

    /** 新增记录数 */
    private long newRecords;

    /** 新增图片数 */
    private long newImages;
}
