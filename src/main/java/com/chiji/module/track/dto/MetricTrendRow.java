package com.chiji.module.track.dto;

import lombok.Data;

/**
 * 接口监控趋势投影行（管理端运维看板折线图用）。
 * <p>
 * 列别名 label / cnt / err_cnt / max_ms 对应驼峰字段；
 * label 为小时两位数字（按小时聚合）或日期/月份字符串（按日/月聚合）。
 */
@Data
public class MetricTrendRow {

    /** 分组标签（小时「09」/ 日期「2026-09-20」/ 月份「2026-09」） */
    private String label;

    /** 调用数 */
    private Long cnt;

    /** 失败数 */
    private Long errCnt;

    /** 最大耗时 ms */
    private Integer maxMs;
}
