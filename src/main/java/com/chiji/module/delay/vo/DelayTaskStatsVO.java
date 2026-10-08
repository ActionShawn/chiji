package com.chiji.module.delay.vo;

import lombok.Data;

import java.util.Map;

/**
 * 延迟任务健康度统计 VO（运维概览卡）。
 * <p>
 * 健康度口径：FAILED 行存在 → RED；平均触发偏差 &gt; 30s 或过期积压 &gt; 100 条 → YELLOW；否则 GREEN。
 * 业务跳过（last_error 有值但状态 DONE）不视为异常。
 */
@Data
public class DelayTaskStatsVO {

    /** 五状态计数（无行的状态补 0）：PENDING/RUNNING/DONE/CANCELLED/FAILED */
    private Map<String, Long> statusCounts;

    /** 今日投递数 */
    private long todaySubmitted;

    /** 今日触发数（finished_at 口径） */
    private long todayFinished;

    /** 过期积压数：PENDING 且 execute_at 已过期超 1 分钟 */
    private long overdueBacklog;

    /** 平均触发偏差（秒），最近 24h 完成且 retry_count=0 的 DONE 行 */
    private double avgDeviationSeconds;

    /** 最大触发偏差（秒），统计口径同上 */
    private double maxDeviationSeconds;

    /** 健康度：GREEN / YELLOW / RED */
    private String healthLevel;
}
