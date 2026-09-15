package com.chiji.module.track.job;

import com.chiji.module.track.service.StatDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 运营日汇总任务。
 * <p>
 * 每日 00:05（Asia/Shanghai）聚合前一日数据入 stat_daily，先删后插幂等；
 * 失败可手动重跑（重新执行 aggregate 即可）。受 {@code chiji.job.enabled} 开关控制
 * （与业务 job 共用，metrics 开关不影响——汇总只读已落库数据）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatDailyAggregateJob {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Value("${chiji.job.enabled:true}")
    private boolean enabled;

    private final StatDailyService statDailyService;

    /**
     * 每日 00:05 聚合前一日。
     */
    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void aggregateYesterday() {
        if (!enabled) {
            return;
        }
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        try {
            statDailyService.aggregate(yesterday);
        } catch (Exception e) {
            // 幂等设计：失败次日重跑或手动重跑修正
            log.error("运营日汇总失败, date={}, 可重跑修正", yesterday, e);
        }
    }
}
