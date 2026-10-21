package com.chiji.module.stage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 开启新阶段请求体。
 * <p>
 * 对应前端「开启新阶段」表单：阶段名称 + 总副数（双模为总组数）+ 佩戴天数 + 开始日期（可空）+ 记录模式。
 * 记录模式与阶段一对多：阶段建时固化 mode，不同模式的数据彼此独立。
 * <p>
 * 校验规则（条件校验在服务层做，BE-FB-03：count 由 DTO @NotNull 迁移为服务层按模式强校验，
 * 固定托槽模式不传 count 即合法，隐形路径保持与原 @NotNull 等价的强校验）：
 * <ul>
 *   <li>mode = CLEAR_SINGLE（或缺省，兼容旧客户端）：count/days 必填，softDays/hardDays 忽略</li>
 *   <li>mode = CLEAR_DUAL：count/softDays/hardDays 必填（品牌不同周期不同，由用户按医嘱填写），days 忽略</li>
 *   <li>mode = FIXED_BRACKET：仅 name（+可选 startDate），count/days 等全部忽略且不落库</li>
 * </ul>
 *
 * @param name      阶段名称（必填，形如「精细调整」）
 * @param mode      记录模式编码（可空，缺省 CLEAR_SINGLE）
 * @param count     总副数（隐形必填 ≥1；双模下语义为总组数；固定托槽不传）
 * @param days      每副佩戴天数（单模必填，≥1）
 * @param softDays  双模：软膜佩戴天数（双模必填，≥1）
 * @param hardDays  双模：硬膜佩戴天数（双模必填，≥1）
 * @param startDate 开始日期（可空，ISO 格式 yyyy-MM-dd）
 * @param startAlignerNum 当前从第几副开始（可空，缺省 1）。其之前的节点置 DONE，本副 ACTIVE，之后 FUTURE；
 *                        双模按物理副序号（每组软+硬 2 个节点，共 2×count）
 */
public record CreateStageRequest(
        @NotBlank(message = "阶段名称不能为空") @Size(max = 64, message = "阶段名称不能超过 64 字") String name,
        String mode,
        @Min(value = 1, message = "总副数至少为 1") Integer count,
        @Min(value = 1, message = "每副天数至少为 1") Integer days,
        @Min(value = 1, message = "软膜天数至少为 1") Integer softDays,
        @Min(value = 1, message = "硬膜天数至少为 1") Integer hardDays,
        String startDate,
        @Min(value = 1, message = "起始副至少为 1") Integer startAlignerNum
) {
}
