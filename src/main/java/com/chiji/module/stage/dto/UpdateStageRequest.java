package com.chiji.module.stage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编辑阶段请求体。
 * <p>
 * 对应前端「编辑阶段」表单：阶段名称 + 总副数（双模为总组数）+ 佩戴天数 + 开始日期（可空）+ 当前起始副。
 * 记录模式（mode）在阶段创建时固化，不允许修改，故本请求不含 mode。
 *
 * @param name      阶段名称（必填，形如「精细调整」）
 * @param count     总副数（必填，≥1；双模下语义为总组数）
 * @param days      每副佩戴天数（单模必填，≥1）
 * @param softDays  双模：软膜佩戴天数（双模必填，≥1）
 * @param hardDays  双模：硬膜佩戴天数（双模必填，≥1）
 * @param startDate 开始日期（可空，ISO 格式 yyyy-MM-dd）
 * @param startAlignerNum 当前从第几副开始（可空，缺省 1）。其之前的节点置 DONE，本副 ACTIVE，之后 FUTURE
 */
public record UpdateStageRequest(
        @NotBlank(message = "阶段名称不能为空") @Size(max = 64, message = "阶段名称不能超过 64 字") String name,
        @NotNull(message = "总副数不能为空") @Min(value = 1, message = "总副数至少为 1") Integer count,
        @Min(value = 1, message = "每副天数至少为 1") Integer days,
        @Min(value = 1, message = "软膜天数至少为 1") Integer softDays,
        @Min(value = 1, message = "硬膜天数至少为 1") Integer hardDays,
        String startDate,
        @Min(value = 1, message = "起始副至少为 1") Integer startAlignerNum
) {
}
