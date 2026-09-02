package com.chiji.module.stage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 开启新阶段请求体。
 * <p>
 * 对应前端「开启新阶段」表单：阶段名称 + 总副数（双模为总组数）+ 佩戴天数 + 开始日期（可空）+ 记录模式。
 * 记录模式与阶段一对多：阶段建时固化 mode，不同模式的数据彼此独立。
 * <p>
 * 校验规则（条件校验在服务层做）：
 * <ul>
 *   <li>mode = CLEAR_SINGLE（或缺省，兼容旧客户端）：days 必填，softDays/hardDays 忽略</li>
 *   <li>mode = CLEAR_DUAL：softDays / hardDays 必填（品牌不同周期不同，由用户按医嘱填写），days 忽略</li>
 * </ul>
 *
 * @param name      阶段名称（必填，形如「精细调整」）
 * @param mode      记录模式编码（可空，缺省 CLEAR_SINGLE）
 * @param count     总副数（必填，≥1；双模下语义为总组数）
 * @param days      每副佩戴天数（单模必填，≥1）
 * @param softDays  双模：软膜佩戴天数（双模必填，≥1）
 * @param hardDays  双模：硬膜佩戴天数（双模必填，≥1）
 * @param startDate 开始日期（可空，ISO 格式 yyyy-MM-dd）
 */
public record CreateStageRequest(
        @NotBlank(message = "阶段名称不能为空") @Size(max = 64, message = "阶段名称不能超过 64 字") String name,
        String mode,
        @NotNull(message = "总副数不能为空") @Min(value = 1, message = "总副数至少为 1") Integer count,
        @Min(value = 1, message = "每副天数至少为 1") Integer days,
        @Min(value = 1, message = "软膜天数至少为 1") Integer softDays,
        @Min(value = 1, message = "硬膜天数至少为 1") Integer hardDays,
        String startDate
) {
}
