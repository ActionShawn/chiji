package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 花费流水视图。
 *
 * @param id           流水 ID
 * @param categoryId   分类 ID
 * @param categoryName 分类名
 * @param visitId      关联复诊记录 ID
 * @param expenseDate  花费日期
 * @param amount       金额（元）
 * @param note         备注
 */
@Schema(description = "花费流水")
public record ClinicExpenseVO(
        Long id,
        Long categoryId,
        String categoryName,
        Long visitId,
        LocalDate expenseDate,
        BigDecimal amount,
        String note) {
}
