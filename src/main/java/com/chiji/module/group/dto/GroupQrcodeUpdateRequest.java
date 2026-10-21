package com.chiji.module.group.dto;

/**
 * 交流群二维码更新请求（全量覆盖语义：以传入地址为准）。
 * <p>
 * 图片字节不经后端：管理员端先走既有 {@code /api/files/upload-url} COS 直传链路拿到
 * {@code url}，再以该 url 调本接口落库。后端仅校验 https 前缀与长度，不回源校验可达性
 * （上传通道已保证文件存在）。
 *
 * @param imageUrl 二维码图片地址（必须 https，长度 ≤ 512）
 */
public record GroupQrcodeUpdateRequest(String imageUrl) {
}
