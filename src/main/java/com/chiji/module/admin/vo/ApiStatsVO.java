package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端接口监控聚合行 VO（区间列表，调用量倒序）。
 */
@Data
@Builder
public class ApiStatsVO {

    /** 接口模板 */
    private String api;

    /** 调用数 */
    private long cnt;

    /** 失败数 */
    private long errCnt;

    /** 失败率（0~1，保留 4 位） */
    private double errRate;

    /** 慢请求数 */
    private long slowCnt;

    /** 最大耗时 ms */
    private int maxMs;
}
