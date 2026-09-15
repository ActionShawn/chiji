package com.chiji.framework.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 采集层专用异步执行器。
 * <p>
 * 登录日志/慢请求明细等低频异步写入共用：JDK21 单虚拟线程顺序消费，
 * 不占用请求线程、不阻塞响应。内置丢弃保护：待处理任务超过 {@link #MAX_QUEUE}
 * 时直接丢弃（后端故障时避免堆积反压业务），自愈后自动恢复。
 */
@Slf4j
@Component
public class TrackAsyncExecutor {

    /** 待处理任务上限，超过即丢弃 */
    private static final int MAX_QUEUE = 500;

    private final AtomicInteger pending = new AtomicInteger();

    /** 单线程虚拟线程执行器（daemon 语义：不阻止 JVM 退出） */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("track-async").factory());

    /**
     * 提交异步任务（超限丢弃，不抛异常）。
     *
     * @param tag  任务标签（日志定位用，如 login-log / slow-api）
     * @param task 任务体
     */
    public void submit(String tag, Runnable task) {
        if (pending.incrementAndGet() > MAX_QUEUE) {
            pending.decrementAndGet();
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.warn("采集异步任务执行失败, tag={}", tag, e);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
        }
    }
}
