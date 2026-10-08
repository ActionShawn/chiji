package com.chiji.module.track.service;

/**
 * 接口计数器刷盘服务。
 * <p>
 * 封装内存计数器快照 → api_metric_hourly 聚合 upsert 的落库链路，
 * 供 {@code MetricsFlushJob} 调度（mapper 仅允许 service 层访问）。
 */
public interface MetricsFlushService {

    /**
     * 取走内存计数器快照并按（当日+小时+接口模板）累加 upsert 入聚合表。
     * 单行失败不阻断其余刷盘。
     */
    void flush();
}
