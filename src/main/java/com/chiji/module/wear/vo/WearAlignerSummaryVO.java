package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 换副历史页 / 阶段下某副牙套的佩戴汇总（/api/wear/aligner/{id}/summary）。
 * <p>
 * 会话时间跨多自然日时，按「会话重叠区间」切分归属到每日自然日，
 * 与该副关联（会话 aligner_id 来自打卡时刻所在 ACTIVE 副）。
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
