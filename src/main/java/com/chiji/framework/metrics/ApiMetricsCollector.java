package com.chiji.framework.metrics;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 接口监控内存计数器。
 * <p>
 * 常驻内存仅一个小 Map（key=归一化接口模板，几十个），桶内 LongAdder 无锁累加，
 * 单次 record 开销纳秒~亚微秒级。刷盘任务通过 {@link #snapshot()} 逐 key 原子搬迁，
 * 交换后未刷盘的数据在服务重启时丢失（≤5 分钟，已接受）。
 */
@Component
public class ApiMetricsCollector {

    /** 当前计数 Map（key=接口模板）。 */
    private final ConcurrentHashMap<String, Bucket> map = new ConcurrentHashMap<>();

    /**
     * 记录一次接口调用。
     *
     * @param api     归一化接口模板
     * @param costMs  耗时 ms
     * @param success 是否成功（afterCompletion 无异常）
     * @param slow    是否超过慢阈值
     */
    public void record(String api, long costMs, boolean success, boolean slow) {
        Bucket bucket = map.computeIfAbsent(api, k -> new Bucket());
        bucket.cnt.increment();
        if (!success) {
            bucket.errCnt.increment();
        }
        if (slow) {
            bucket.slowCnt.increment();
        }
        bucket.maxMs.accumulateAndGet(costMs, Math::max);
    }

    /**
     * 取走当前全部计数（快照）。
     * <p>
     * 并发安全性：搬迁中 record 的累加要么发生在已搬走的桶对象上（计入本快照），
     * 要么新建桶留在 map 中（计入下个窗口），数据不丢、不双计。
     *
     * @return 自上次快照以来的计数（key=接口模板，可能为空 Map）
     */
    public Map<String, Bucket> snapshot() {
        Map<String, Bucket> taken = new HashMap<>();
        map.forEach((key, ignored) -> {
            Bucket bucket = map.remove(key);
            if (bucket != null) {
                taken.put(key, bucket);
            }
        });
        return taken;
    }

    /**
     * 单接口计数桶。
     */
    public static class Bucket {
        /** 调用数 */
        final LongAdder cnt = new LongAdder();
        /** 失败数 */
        final LongAdder errCnt = new LongAdder();
        /** 慢请求数 */
        final LongAdder slowCnt = new LongAdder();
        /** 最大耗时 ms */
        final AtomicLong maxMs = new AtomicLong();

        public long getCnt() {
            return cnt.sum();
        }

        public long getErrCnt() {
            return errCnt.sum();
        }

        public long getSlowCnt() {
            return slowCnt.sum();
        }

        public long getMaxMs() {
            return maxMs.get();
        }

        /** 是否全部为零（刷盘时跳过空桶）。 */
        public boolean isEmpty() {
            return cnt.sum() == 0 && errCnt.sum() == 0 && slowCnt.sum() == 0 && maxMs.get() == 0;
        }
    }
}
