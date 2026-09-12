package com.chiji.module.stage.vo;

import lombok.Builder;

/**
 * 阶段详情 VO（编辑阶段表单回显）。
 *
 * @param id              阶段 ID（雪花算法，序列化为字符串）
 * @param name            阶段名称
 * @param mode            记录模式（CLEAR_SINGLE / CLEAR_DUAL，创建后不可改）
 * @param count           总副数（双模为总组数）
 * @param days            每副佩戴天数（单模）
 * @param softDays        双模软膜天数
 * @param hardDays        双模硬膜天数
 * @param startDate       开始日期 yyyy-MM-dd（可空）
 * @param startAlignerNum 当前起始副序号（ACTIVE 副 num；无 ACTIVE 副回退 1）
 */
@Builder
public record StageDetailVO(
        Long id,
        String name,
        String mode,
        Integer count,
        Integer days,
        Integer softDays,
        Integer hardDays,
        String startDate,
        Integer startAlignerNum
) {
}
