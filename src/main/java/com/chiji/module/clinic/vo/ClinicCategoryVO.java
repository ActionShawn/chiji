package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 花费分类视图（内置 + 自定义合并后列表项）。
 *
 * @param id        分类 ID
 * @param name      分类名
 * @param builtIn   是否系统内置（内置不可改删）
 * @param sortOrder 排序权重
 */
@Schema(description = "花费分类")
public record ClinicCategoryVO(
        Long id,
        String name,
        Boolean builtIn,
        Integer sortOrder) {
}
