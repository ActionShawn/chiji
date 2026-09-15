package com.chiji.module.track.dto;

import lombok.Data;

/**
 * 接口区间聚合投影行（管理端监控列表）。
 * <p>
 * MyBatis 自动映射：列别名 api / cnt / err_cnt / slow_cnt / max_ms。
 */
@Data
public class ApiStatsRow {

    /** 接口模板 */
    private String api;

    /** 调用数 */
    private Long cnt;

    /** 失败数 */
    private Long errCnt;

    /** 慢请求数 */
    private Long slowCnt;

    /** 最大耗时 ms */
    private Integer maxMs;
}
