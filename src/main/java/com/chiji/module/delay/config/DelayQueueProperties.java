package com.chiji.module.delay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 延迟队列组件配置属性（前缀 {@code chiji.delay.queue}，与项目自定义配置统一 chiji 前缀）。
 * <p>
 * {@code enabled=false} 时为组件总回滚开关：泵停转、submit 静默跳过（不抛异常，
 * 业务动作不得因队列停用而失败），存量 PENDING 行保留、重启开启即恢复。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.delay.queue")
public class DelayQueueProperties {

    /** 组件总开关（回滚开关） */
    private boolean enabled = true;

    /** 泵轮询间隔 ms（到期触发误差 ≤ 泵间隔） */
    private long pollIntervalMs = 10000;

    /** 泵单轮最大取出任务数 */
    private int batchSize = 50;

    /** RUNNING 租约时长（秒），必须大于 handler 最大执行时长，否则执行中任务会被回收重投 */
    private int leaseSeconds = 120;

    /** 最大重试次数：retry_count 超过该值置 FAILED（单任务最多执行 maxRetry + 1 次） */
    private int maxRetry = 5;

    /** 重试退避基数（秒）：第 k 次重试顺延 base × 2^(k-1)，即 30s/60s/120s/... */
    private int retryBackoffBaseSeconds = 30;

    /** 终态行保留天数（CleanJob 每日按 finished_at 物理清理） */
    private int retentionDays = 30;
}
