package com.chiji.module.rubberband.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 橡皮筋近 7 天打卡概览 VO（GET /api/rubber-band/recent，REQ-FB-006）。
 * <p>
 * 同时作为首页 summary 固定面板 rubberBand 字段的载荷（BE-FB-09 并入，零新增请求）。
 *
 * @param todayDone 今日是否已打卡（无行 = false）
 * @param days      近 7 天点阵数据（7 项，日期升序即最旧在最左、今天在最右；
 *                  无打卡记录的日期 done=false，前端空描边渲染）
 */
public record RubberBandRecentVO(
        Boolean todayDone,
        List<RubberBandDayVO> days
) {

    /**
     * 单日打卡状态。
     *
     * @param date 打卡日（ISO yyyy-MM-dd）
     * @param done 是否已打卡（done=1 才为 true，取消态 false）
     */
    public record RubberBandDayVO(
            LocalDate date,
            Boolean done
    ) {
    }
}
