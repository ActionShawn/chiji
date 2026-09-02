// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\storage\StoredFile.java
package com.chiji.framework.storage;

/**
 * 文件存储结果（不可变）。
 * <p>
 * 由 {@link FileStorageStrategy#store} 返回，url 直接落 {@code record_media.url}。
 */
public record StoredFile(
        /** 对外可访问 URL（base-url + 相对路径），落库 record_media.url */
        String url,
        /** 相对路径（userId/yyyy/MM/dd/snowflake.ext），用于日志诊断 */
        String relativePath,
        /** 文件字节数 */
        long size,
        /** 内容类型，如 image/jpeg、video/mp4 */
        String contentType,
        /** 原始文件名（仅供日志） */
        String originalFilename) {
}
