package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 佩戴统计（/api/wear/stats）。
 *
 * @param range         天数窗口：LAST_7 / LAST_30
 * @param dates         逐日 yyyy-MM-dd（正序）
 * @param daySeconds    逐日佩戴秒（无记录日为 0，长度同 dates）
 * @param goalSec       目标秒
 * @param fullDays      达标天数
 * @param partialDays   部分达标天数
 * @param noneDays      未达标天数
 * @param avgSeconds    有记录日平均佩戴秒（0 天则为 0）
 * @param bestDate      最优日 yyyy-MM-dd（可空）
 * @param bestSeconds   最优日秒
 */
public record WearStatsVO(
        String range,
        List<String> dates,
        List<Long> daySeconds,
        Integer goalSec,
        Integer fullDays,
        Integer partialDays,
        Integer noneDays,
        Long avgSeconds,
        String bestDate,
        Long bestSeconds
) {
}
