package com.chiji.module.track.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.LoginLog;
import com.chiji.entity.SlowApiLog;
import com.chiji.entity.UsageSession;
import com.chiji.framework.metrics.MetricsProperties;
import com.chiji.module.track.mapper.LoginLogMapper;
import com.chiji.module.track.mapper.SlowApiLogMapper;
import com.chiji.module.track.mapper.UsageSessionMapper;
import com.chiji.module.track.service.MetricsCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 采集明细清理实现：分批 LIMIT 删至无行可删；单表异常不阻断其余表清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCleanupServiceImpl implements MetricsCleanupService {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 单批删除行数 */
    private static final long BATCH = 1000;

    /** 单表单轮最大批次数（防御异常数据量） */
    private static final int MAX_BATCH_ROUNDS = 200;

    private final MetricsProperties metricsProperties;
    private final SlowApiLogMapper slowApiLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final UsageSessionMapper usageSessionMapper;

    @Override
    public int cleanSlowApiLog() {
        LocalDateTime threshold = LocalDateTime.now(ZONE).minusDays(metricsProperties.getSlowLogRetentionDays());
        return deleteInBatches("slow_api_log", () -> slowApiLogMapper.delete(
                new LambdaQueryWrapper<SlowApiLog>()
                        .lt(SlowApiLog::getCreatedAt, threshold)
                        .last("LIMIT " + BATCH)));
    }

    @Override
    public int cleanLoginLog() {
        LocalDateTime threshold = LocalDateTime.now(ZONE).minusDays(metricsProperties.getLoginLogRetentionDays());
        return deleteInBatches("login_log", () -> loginLogMapper.delete(
                new LambdaQueryWrapper<LoginLog>()
                        .lt(LoginLog::getLoginAt, threshold)
                        .last("LIMIT " + BATCH)));
    }

    @Override
    public int cleanUsageSession() {
        LocalDateTime threshold = LocalDateTime.now(ZONE).minusDays(metricsProperties.getUsageSessionRetentionDays());
        return deleteInBatches("usage_session", () -> usageSessionMapper.delete(
                new LambdaQueryWrapper<UsageSession>()
                        .lt(UsageSession::getEnterAt, threshold)
                        .last("LIMIT " + BATCH)));
    }

    /** 分批循环删除至无行可删；单表异常不阻断其余表清理。 */
    private int deleteInBatches(String table, java.util.function.IntSupplier deleteOnce) {
        int total = 0;
        try {
            for (int i = 0; i < MAX_BATCH_ROUNDS; i++) {
                int deleted = deleteOnce.getAsInt();
                total += deleted;
                if (deleted < BATCH) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("采集明细清理失败, table={}, 已删 {}", table, total, e);
        }
        return total;
    }
}
