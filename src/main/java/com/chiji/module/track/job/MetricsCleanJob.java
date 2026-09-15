package com.chiji.module.track.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.LoginLog;
import com.chiji.entity.SlowApiLog;
import com.chiji.entity.UsageSession;
import com.chiji.framework.metrics.MetricsProperties;
import com.chiji.module.track.mapper.LoginLogMapper;
import com.chiji.module.track.mapper.SlowApiLogMapper;
import com.chiji.module.track.mapper.UsageSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 采集明细清理任务。
 * <p>
 * 每日 03:00（Asia/Shanghai）按保留期物理删除明细表（分批 LIMIT 删，避免长事务）：
 * slow_api_log 90 天 / login_log 365 天 / usage_session 180 天（均可配）。
 * 聚合表（api_metric_hourly / stat_daily）永久保留不清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCleanJob {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 单批删除行数 */
    private static final long BATCH = 1000;

    /** 单表单轮最大批次数（防御异常数据量） */
    private static final int MAX_BATCH_ROUNDS = 200;

    @Value("${chiji.job.enabled:true}")
    private boolean jobEnabled;

    private final MetricsProperties metricsProperties;
    private final SlowApiLogMapper slowApiLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final UsageSessionMapper usageSessionMapper;

    /**
     * 每日 03:00 清理超保留期明细。
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void clean() {
        if (!jobEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        int slowDeleted = cleanSlowApiLog(now);
        int loginDeleted = cleanLoginLog(now);
        int sessionDeleted = cleanUsageSession(now);
        if (slowDeleted + loginDeleted + sessionDeleted > 0) {
            log.info("采集明细清理完成, slowApi={}, loginLog={}, usageSession={}",
                    slowDeleted, loginDeleted, sessionDeleted);
        }
    }

    private int cleanSlowApiLog(LocalDateTime now) {
        return deleteInBatches("slow_api_log", () -> slowApiLogMapper.delete(
                new LambdaQueryWrapper<SlowApiLog>()
                        .lt(SlowApiLog::getCreatedAt, now.minusDays(metricsProperties.getSlowLogRetentionDays()))
                        .last("LIMIT " + BATCH)));
    }

    private int cleanLoginLog(LocalDateTime now) {
        return deleteInBatches("login_log", () -> loginLogMapper.delete(
                new LambdaQueryWrapper<LoginLog>()
                        .lt(LoginLog::getLoginAt, now.minusDays(metricsProperties.getLoginLogRetentionDays()))
                        .last("LIMIT " + BATCH)));
    }

    private int cleanUsageSession(LocalDateTime now) {
        return deleteInBatches("usage_session", () -> usageSessionMapper.delete(
                new LambdaQueryWrapper<UsageSession>()
                        .lt(UsageSession::getEnterAt, now.minusDays(metricsProperties.getUsageSessionRetentionDays()))
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
