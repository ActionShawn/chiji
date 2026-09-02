package com.chiji.framework.storage;

/**
 * 客户端直传签名信息（不可变）。
 * <p>
 * 由存储策略 {@link FileStorageStrategy#presign(String, String)} 签发，
 * 小程序凭 {@code uploadUrl}（PUT 预签名地址）将文件直传 COS，随后以 {@code url} 落库展示。
 *
 * @param uploadUrl  客户端 PUT 上传的目标地址（带签名，短时有效）
 * @param url        上传完成后对外可访问的最终 URL（base-url + 相对路径）
 * @param contentType 上传对象的 Content-Type（如 image/jpeg），前端 PUT 请求头需保持一致
 */
public record DirectUpload(String uploadUrl, String url, String contentType) {
}
