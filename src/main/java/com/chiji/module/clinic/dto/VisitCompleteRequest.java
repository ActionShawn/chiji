package com.chiji.module.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 标记就诊完成请求（PLANNED → DONE，补录内容/照片/复诊费/下次复诊日）。
 *
 * @param content          就诊内容摘要（可空，空则用默认文案）
 * @param mediaList        照片/视频媒体列表（可空；挂到同步生成的时光轴记录上）
 * @param nextVisitDate    医生约定的下次复诊日期（可空；填写自动生成下一条 PLANNED）
 * @param expenseAmount    一键带出的复诊费金额（可空；&gt;0 时生成一笔流水）
 * @param expenseCategoryId复诊费分类（可空；空则用内置「复诊费」）
 * @param expenseNote      复诊费备注（可空）
 */
@Schema(description = "标记就诊完成请求")
public record VisitCompleteRequest(
        @Schema(description = "就诊内容摘要") @Size(max = 512, message = "内容过长") String content,
        @Schema(description = "媒体列表") List<@Valid MediaItem> mediaList,
        @Schema(description = "下次复诊日期") LocalDate nextVisitDate,
        @Schema(description = "一键带出复诊费金额（空或 0 不生成）")
        @DecimalMin(value = "0", message = "金额不合法") BigDecimal expenseAmount,
        @Schema(description = "复诊费分类 ID（空=内置复诊费）") Long expenseCategoryId,
        @Schema(description = "复诊费备注") String expenseNote) {

    /**
     * 媒体条目（沿用时光轴媒体结构）。
     *
     * @param url  媒体地址（必填）
     * @param type 类型：IMAGE / VIDEO（缺省 IMAGE）
     */
    @Schema(description = "媒体条目")
    public record MediaItem(
            @Schema(description = "媒体地址") String url,
            @Schema(description = "类型：IMAGE / VIDEO") String type) {
    }
}
