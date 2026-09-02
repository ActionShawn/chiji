package com.chiji.module.file.vo;

/**
 * 获取直传签名响应（不可变）。
 * <p>
 * 小程序拿到 {@code uploadUrl} 后以 PUT 直传 COS，完成后再用 {@code url} 作为
 * 正式媒体地址落库（与 {@link FileUploadVO#url()} 同语义）。
 *
 * @param uploadUrl   PUT 上传地址（预签名，短时有效）
 * @param url         最终可访问 URL（供保存记录时填入 medias.url）
 * @param type        媒体类型：IMAGE / VIDEO
 * @param contentType 建议的 PUT Content-Type（image/jpeg 等）
 * @param fileName    服务端登记的原始文件名
 */
public record UploadUrlVO(
        String uploadUrl,
        String url,
        String type,
        String contentType,
        String fileName) {
}
