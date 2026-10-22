package com.chiji.module.rubberband.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 橡皮筋近 7 天计数概览 VO（GET /api/rubber-band/recent，OPT-FB-04 次数语义升级）。
 * <p>
 * 同时作为首页 summary 固定面板 rubberBand 字段的载荷（零新增请求）。
 * 第 1 批字段 {@code todayDone / done} 废止；今日是否打卡以 {@code todayCount > 0} 判定（≥1 次即当日打卡）。
 *
 * @param todayCount 今日累计更换次数（无行 = 0）
 * @param days       近 7 天点阵数据（7 项，日期升序即最旧在最左、今天在最右；
 *                   无记录日 count=0，前端空描边渲染）
 * @param streakDays 连续打卡天数（count&gt;0 连续自然日，服务端聚合；今天未计数不断签，
 *                   从昨天往回数——当日尚未结束不视为中断，BE-C4 聚合口径）
 * @param weekCount  本周（周一起点）累计更换次数（SUM(count)，服务端聚合；与前端 §4.4 口径一致）
 */
public record RubberBandRecentVO(
        Integer todayCount,
        List<RubberBandDayVO> days,
        Integer streakDays,
        Integer weekCount
) {

    /**
     * 单日计数。
     *
     * @param date  记录日（ISO yyyy-MM-dd）
     * @param count 当日累计更换次数（0 = 无记录/历史取消行）
     */
    public record RubberBandDayVO(
            LocalDate date,
            Integer count
    ) {
    }
}
