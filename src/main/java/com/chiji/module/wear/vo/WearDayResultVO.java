package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 佩戴数据结算/统计页逐日结果。
 *
 * @param date      自然日 yyyy-MM-dd
 * @param summaryDate 是否已结算（结算过才有 settledAt）
 * @param wearSec   当日总佩戴秒（含 makeup）
 * @param makeupSec 当日校准补录秒
 * @param level     达标档 FULL / PARTIAL / NONE（未结算当日为实时推测）
 */
public record WearDayResultVO(
        String date,
        Long wearSec,
        Long makeupSec,
        String level
) {
    /** 结算 API 可能返回多日，直接复用 */
    public static WearDayResultVO of(String date, long wearSec, long makeupSec, String level) {
        return new WearDayResultVO(date, wearSec, makeupSec, level);
    }
}
