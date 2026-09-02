// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\module\stage\dto\MediaItem.java
package com.chiji.module.stage.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 记录媒体元素（创建记录请求体 medias 数组的元素）。
 * <p>
 * url 由前端先调 {@code POST /api/files/upload} 获取后填入。
 */
public record MediaItem(
        /** 媒体类型：IMAGE / VIDEO */
        @NotBlank(message = "媒体类型不能为空")
        String type,
        /** 可访问 URL（上传接口返回的 url） */
        @NotBlank(message = "媒体地址不能为空")
        String url,
        /** 视频时长（秒），图片为 null */
        Integer duration,
        /** 展示排序，越小越靠前（可空，缺省按数组下标） */
        Integer sortOrder
) {
}
