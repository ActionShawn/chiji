package com.chiji.module.rubberband.dto;

/**
 * 橡皮筋单日计数投影行（近 7 天点阵数据源，OPT-FB-04 次数语义）。
 * <p>
 * MyBatis 自动映射：列别名 date / count 对应驼峰字段。
 *
 * @param date  记录日（ISO yyyy-MM-dd）
 * @param count 当日累计更换次数（0 = 无记录/已取消语义的历史行）
 */
public record RubberBandDayRow(
        java.time.LocalDate date,
        Integer count
) {
}
