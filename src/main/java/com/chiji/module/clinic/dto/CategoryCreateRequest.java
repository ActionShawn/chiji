package com.chiji.module.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增自定义花费分类请求。
 *
 * @param name 分类名（必填，1~32 字符，同用户下唯一）
 */
@Schema(description = "新增自定义花费分类请求")
public record CategoryCreateRequest(
        @Schema(description = "分类名") @NotBlank(message = "分类名不能为空")
        @Size(max = 32, message = "分类名过长") String name) {
}
