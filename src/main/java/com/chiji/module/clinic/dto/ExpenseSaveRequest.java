package com.chiji.module.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建/编辑花费流水请求。
 *
 * @param categoryId  分类 ID（必填）
 * @param expenseDate 花费日期（必填）
 * @param amount      金额（必填，&gt;0，两位小数）
 * @param note        备注（可空）
 * @param visitId     关联复诊记录 ID（可空；复诊费一键带出时回填）
 */
@Schema(description = "创建/编辑花费流水请求")
public record ExpenseSaveRequest(
        @Schema(description = "分类 ID") @NotNull Long categoryId,
        @Schema(description = "花费日期") @NotNull LocalDate expenseDate,
        @Schema(description = "金额（元）") @NotNull
        @DecimalMin(value = "0.01", message = "金额需大于 0")
        @Digits(integer = 8, fraction = 2, message = "金额最多两位小数")
        BigDecimal amount,
        @Schema(description = "备注") @Size(max = 255, message = "备注过长") String note,
        @Schema(description = "关联复诊记录 ID") Long visitId) {
}
