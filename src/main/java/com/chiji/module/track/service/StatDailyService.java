package com.chiji.module.track.service;

import java.time.LocalDate;

/**
 * 运营日汇总服务。
 * <p>
 * 聚合指定自然日（Asia/Shanghai）的各采集表数据写入 stat_daily，
 * 先删后插幂等，任务失败重跑自动修正。
 */
public interface StatDailyService {

    /**
     * 聚合指定日期数据并落 stat_daily（幂等）。
     *
     * @param date 统计日期
     */
    void aggregate(LocalDate date);
}
