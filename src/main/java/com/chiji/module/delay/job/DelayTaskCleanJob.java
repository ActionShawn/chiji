package com.chiji.module.delay.job;

import com.chiji.module.delay.config.DelayQueueProperties;
import com.chiji.module.delay.service.DelayTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 延迟任务终态行清理任务：每日 03:40（Asia/Shanghai）物理删除
 * finished_at 超过 {@code delay.queue.retention-days}（默认 30 天）的
 * DONE/CANCELLED/FAILED 行，防表膨胀。PENDING/RUNNING 行不清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayTaskCleanJob {

    private final DelayTaskService delayTaskService;
    private final DelayQueueProperties properties;

    /**
     * 每日 03:40 清理超保留期终态行。
     */
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Shanghai")
    public void clean() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int deleted = delayTaskService.cleanExpiredRows();
            if (deleted > 0) {
                log.info("延迟任务终态行清理完成: retentionDays={}, 删除={}", properties.getRetentionDays(), deleted);
            }
        } catch (Exception e) {
            log.error("延迟任务终态行清理异常", e);
        }
    }
}
