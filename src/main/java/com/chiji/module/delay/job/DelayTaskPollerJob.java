package com.chiji.module.delay.job;

import com.chiji.entity.DelayTask;
import com.chiji.module.delay.config.DelayQueueProperties;
import com.chiji.module.delay.service.DelayTaskService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 延迟队列调度泵。
 * <p>
 * 每轮（fixedDelay = {@code delay.queue.poll-interval-ms}，默认 10s）执行：
 * <ol>
 *   <li>回收超时租约（执行中实例崩溃/卡死超过 lease-seconds 的任务重新 PENDING）；</li>
 *   <li>兜底置 FAILED：租约回收导致 retry_count 超上限的行（防无限回收循环）；</li>
 *   <li>SKIP LOCKED 原子取出到期任务置 RUNNING（单事务，见 DelayTaskService#claimDueTasks），
 *       分发到虚拟线程执行，泵立即返回继续下一轮。</li>
 * </ol>
 * 多实例并发安全由 SKIP LOCKED + biz_key 唯一约束 + handler 业务校验三层防护；
 * 不做 leader 选举（容器环境实例标识不可靠，SKIP LOCKED 已覆盖）。
 * {@code delay.queue.enabled=false} 时整轮跳过（组件回滚开关，存量 PENDING 行保留）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayTaskPollerJob {

    private final DelayTaskService delayTaskService;
    private final DelayQueueProperties properties;

    /** 虚拟线程执行器：handler 长耗时执行不阻塞泵轮询（JDK21 已全局启用虚拟线程） */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 泵轮询入口。
     */
    @Scheduled(fixedDelayString = "${delay.queue.poll-interval-ms:10000}",
            initialDelayString = "${delay.queue.poll-interval-ms:10000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int reclaimed = delayTaskService.reclaimExpiredLeases();
            if (reclaimed > 0) {
                log.warn("延迟任务租约到期回收: {} 条", reclaimed);
            }
            int exhausted = delayTaskService.failExhaustedPending();
            if (exhausted > 0) {
                log.warn("延迟任务租约回收后重试次数达上限，置 FAILED: {} 条", exhausted);
            }
            List<DelayTask> claimed = delayTaskService.claimDueTasks(properties.getBatchSize());
            for (DelayTask task : claimed) {
                executor.execute(() -> {
                    try {
                        delayTaskService.executeClaimed(task);
                    } catch (Exception e) {
                        // 执行包装器自身异常：任务仍在 RUNNING，等待租约回收重投
                        log.error("延迟任务执行包装器异常: bizKey={}", task.getBizKey(), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("延迟队列泵轮询异常", e);
        }
    }

    /**
     * 停机时等待在途 handler 执行结束（避免发布重启期间任务被中断后等待租约回收）。
     */
    @PreDestroy
    public void shutdown() {
        executor.close();
    }
}
