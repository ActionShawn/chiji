package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 换副历史页 / 阶段下某副牙套的佩戴汇总（/api/wear/aligner/{id}/summary）。
 * <p>
 * 按会话的 {@code aligner_id} 归属统计：仅累计归属该副的会话，跨午夜不预拆，按
 * {@code [当日00:00, 次日00:00)} 逐日切分后累加。
 *
 * @param alignerId   牙套副 id
 * @param totalSec    佩戴本副累计总时长（秒）
 * @param days        该副产生的佩戴日数
 * @param fullDays    达标日数（会话区间内达到当日目标的日子）
 * @param dayWearSec  逐自然日佩戴秒（升序；含无记录日补 0 的连续性由前端处理，此处仅列有贡献日）
 */
public record WearAlignerSummaryVO(
        Long alignerId,
        Long totalSec,
        Integer days,
        Integer fullDays,
        List<DaySec> dayWearSec
) {

    /**
     * 单自然日佩戴秒。
     *
     * @param date yyyy-MM-dd
     * @param sec  秒
     */
    public record DaySec(String date, Long sec) {
    }
}
