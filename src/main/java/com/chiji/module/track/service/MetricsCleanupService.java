package com.chiji.module.track.service;

/**
 * 采集明细清理服务。
 * <p>
 * 按保留期物理删除明细表（分批 LIMIT 删，避免长事务）：
 * slow_api_log 90 天 / login_log 365 天 / usage_session 180 天（均可配）。
 * 聚合表（api_metric_hourly / stat_daily）永久保留不清理。
 * 供 {@code MetricsCleanJob} 调度（mapper 仅允许 service 层访问）。
 */
public interface MetricsCleanupService {

    /** 清理超保留期的慢请求明细，返回删除行数 */
    int cleanSlowApiLog();

    /** 清理超保留期的登录日志，返回删除行数 */
    int cleanLoginLog();

    /** 清理超保留期的使用会话明细，返回删除行数 */
    int cleanUsageSession();
}
