package com.chiji.module.wear.dto;

/**
 * 晨起补记请求（昨晚睡前就戴上了，今早补记）。补记段 source=MANUAL（真实佩戴），
 * 归属按自然日切分，不计入 makeup_sec。
 *
 * @param date         昨晚的自然日 yyyy-MM-dd（通常 = 今天-1）
 * @param startTime    昨晚戴上的时刻 HH:mm（date 当天）
 * @param stillWearing 是否现在还戴着（true 则留佩戴中会话；false 需给 endTime）
 * @param endTime      已摘下时刻 HH:mm（今天当天，需不晚于当前），stillWearing=false 时必填
 */
public record WearMorningBackfillRequest(
        String date,
        String startTime,
        Boolean stillWearing,
        String endTime
) {
}
