package com.chiji.module.track.dto;

import lombok.Data;

/**
 * 接口单小时聚合投影行（管理端监控小时视图）。
 * <p>
 * MyBatis 自动映射：列别名 hour / cnt / err_cnt / slow_cnt / max_ms。
 */
@Data
public class ApiHourlyRow {

    /** 小时 0-23 */
    private Integer hour;

    /** 调用数 */
    private Long cnt;

    /** 失败数 */
    private Long errCnt;

    /** 慢请求数 */
    private Long slowCnt;

    /** 最大耗时 ms */
    private Integer maxMs;
}
