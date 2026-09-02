package com.chiji.module.file.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 获取直传签名请求体（小程序主通道）。
 * <p>
 * 前端仅携带文件名（扩展名用于判定媒体类型）与视频时长，后端签发 COS PUT 预签名地址，
 * 文件内容由小程序直传 COS，不再经过云托管请求体。
 *
 * @param fileName 文件名（扩展名用于判定媒体类型，如 media_1730000000.jpg）
 * @param duration 视频时长（秒），图片不传
 */
public record UploadUrlRequest(
        @NotBlank(message = "文件名不能为空") String fileName,
        Integer duration
) {
}
