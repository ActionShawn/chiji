package com.chiji.module.rubberband.dto;

/**
 * 橡皮筋打卡日期状态投影行（近 7 天点阵数据源）。
 * <p>
 * MyBatis 自动映射：列别名 date / done 对应驼峰字段。
 *
 * @param date 打卡日（ISO yyyy-MM-dd）
 * @param done 1 已打卡 / 0 已取消
 */
public record RubberBandDayRow(
        java.time.LocalDate date,
        Integer done
) {
}
