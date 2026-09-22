package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 花费统计视图。
 *
 * @param total      累计总花费（元）
 * @param byCategory 分类占比（按金额降序；pct 为 0~100 一位小数）
 * @param monthly    近 12 个月趋势（升序，含当月；无数据月金额为 0）
 */
@Schema(description = "花费统计")
public record ExpenseStatsVO(
        BigDecimal total,
        List<CategoryStat> byCategory,
        List<MonthAmount> monthly) {

    /**
     * 分类小计。
     *
     * @param categoryId 分类 ID
     * @param name       分类名
     * @param amount     小计金额
     * @param pct        占比（0~100）
     */
    @Schema(description = "分类小计")
    public record CategoryStat(Long categoryId, String name, BigDecimal amount, Double pct) {
    }

    /**
     * 月度金额。
     *
     * @param month  月份 yyyy-MM
     * @param amount 当月合计
     */
    @Schema(description = "月度金额")
    public record MonthAmount(String month, BigDecimal amount) {
    }
}
