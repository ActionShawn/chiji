package com.chiji.module.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 分类排序请求（全量顺序覆盖；内置分类固定排最前，不参与重排）。
 *
 * @param ids 自定义分类 ID 列表（按期望顺序全量提交）
 */
@Schema(description = "分类排序请求")
public record CategorySortRequest(
        @Schema(description = "自定义分类 ID 顺序") @NotNull List<Long> ids) {
}
