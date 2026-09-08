package com.chiji.module.wear.dto;

import java.util.List;

/**
 * 校准/补录请求（补录忘记打卡的时间段，最多最近 7 天，一次一个自然日）。
 * <p>
 * 补录段 source=MAKEUP、按 {@code date} 全额归属、与已有段互不重叠、每段 ≤24h。
 *
 * @param date     补录目标自然日 yyyy-MM-dd（今天-7 ~ 昨天）
 * @param segments 时间段列表（同日内不重叠；start/end 均为 HH:mm）
 */
public record WearMakeupRequest(String date, List<MakeupSegment> segments) {

    /**
     * 单段时间（HH:mm，均可为「今天」之前的当日时刻）。
     *
     * @param start 开始 HH:mm
     * @param end   结束 HH:mm
     */
    public record MakeupSegment(String start, String end) {
    }
}
