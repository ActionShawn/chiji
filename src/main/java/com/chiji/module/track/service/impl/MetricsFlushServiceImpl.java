package com.chiji.module.track.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.entity.ApiMetricHourly;
import com.chiji.framework.metrics.ApiMetricsCollector;
import com.chiji.module.track.mapper.ApiMetricHourlyMapper;
import com.chiji.module.track.service.MetricsFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 接口计数器刷盘实现。
 * <p>
 * 每 5 分钟取走内存计数器快照，按（刷盘时刻所在日期+小时+接口模板）累加 upsert
 * 入 api_metric_hourly。小时归属为近似口径（误差 ≤5 分钟，日级看板无影响）；
 * 服务重启丢最后 ≤5 分钟计数（已接受）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsFlushServiceImpl implements MetricsFlushService {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ApiMetricsCollector collector;
    private final ApiMetricHourlyMapper apiMetricHourlyMapper;

    @Override
    public void flush() {
        Map<String, ApiMetricsCollector.Bucket> snapshot = collector.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        int hour = now.getHour();

        int rows = 0;
        for (Map.Entry<String, ApiMetricsCollector.Bucket> entry : snapshot.entrySet()) {
            ApiMetricsCollector.Bucket bucket = entry.getValue();
            if (bucket.isEmpty()) {
                continue;
            }
            ApiMetricHourly row = new ApiMetricHourly();
            row.setId(IdWorker.getId());
            row.setStatDate(today);
            row.setHour(hour);
            row.setApi(entry.getKey());
            row.setCnt(bucket.getCnt());
            row.setErrCnt(bucket.getErrCnt());
            row.setSlowCnt(bucket.getSlowCnt());
            row.setMaxMs((int) Math.min(bucket.getMaxMs(), Integer.MAX_VALUE));
            try {
                rows += apiMetricHourlyMapper.upsertAccumulate(row);
            } catch (Exception e) {
                // 单行失败不阻断其余刷盘；丢失计数仅本窗口（下次从零累计）
                log.warn("接口聚合刷盘单行失败, api={}", entry.getKey(), e);
            }
        }
        if (rows > 0) {
            log.info("接口计数器刷盘完成, apis={}, rows={}, window={}T{}",
                    snapshot.size(), rows, today, hour);
        }
    }
}
