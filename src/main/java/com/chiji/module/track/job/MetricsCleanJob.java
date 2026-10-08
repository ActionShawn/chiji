package com.chiji.module.track.job;

import com.chiji.module.track.service.MetricsCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采集明细清理任务（每日 03:00，Asia/Shanghai）。
 * <p>
 * 开关 {@code chiji.job.enabled}；清理逻辑（分批删+保留期）见 {@link MetricsCleanupService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCleanJob {

    @Value("${chiji.job.enabled:true}")
    private boolean jobEnabled;

    private final MetricsCleanupService metricsCleanupService;

    /**
     * 每日 03:00 清理超保留期明细。
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void clean() {
        if (!jobEnabled) {
            return;
        }
        int slowDeleted = metricsCleanupService.cleanSlowApiLog();
        int loginDeleted = metricsCleanupService.cleanLoginLog();
        int sessionDeleted = metricsCleanupService.cleanUsageSession();
        if (slowDeleted + loginDeleted + sessionDeleted > 0) {
            log.info("采集明细清理完成, slowApi={}, loginLog={}, usageSession={}",
                    slowDeleted, loginDeleted, sessionDeleted);
        }
    }
}
