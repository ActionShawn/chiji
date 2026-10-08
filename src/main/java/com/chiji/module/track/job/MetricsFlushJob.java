package com.chiji.module.track.job;

import com.chiji.framework.metrics.MetricsProperties;
import com.chiji.module.track.service.MetricsFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 接口计数器刷盘任务（每 5 分钟，fixedDelay：上轮完成后再计时，避免堆积）。
 * <p>
 * 双开关：{@code chiji.job.enabled} + {@code chiji.metrics.enabled}；
 * 落库逻辑见 {@link MetricsFlushService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsFlushJob {

    @Value("${chiji.job.enabled:true}")
    private boolean jobEnabled;

    private final MetricsProperties metricsProperties;
    private final MetricsFlushService metricsFlushService;

    /**
     * 每 5 分钟刷盘。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, zone = "Asia/Shanghai")
    public void flush() {
        if (!jobEnabled || !metricsProperties.isEnabled()) {
            return;
        }
        metricsFlushService.flush();
    }
}
