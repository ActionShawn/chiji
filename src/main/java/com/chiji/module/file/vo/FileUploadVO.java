// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\module\file\vo\FileUploadVO.java
package com.chiji.module.file.vo;

/**
 * 文件上传响应（不可变）。
 * <p>
 * 上传成功后返回前端，url 供后续保存记录时填入 {@code medias} 列表引用。
 */
public record FileUploadVO(
        /** 可访问 URL（直接落 record_media.url） */
        String url,
        /** 媒体类型：IMAGE / VIDEO */
        String type,
        /** 视频时长（秒），图片为 null */
        Integer duration,
        /** 文件字节数 */
        long size,
        /** 原始文件名（仅供日志展示） */
        String originalName) {
}
