package com.chiji.module.group.vo;

import java.time.LocalDateTime;

/**
 * 用户交流群二维码视图。
 * <p>
 * {@code updatedAt} 为 LocalDateTime，经全局 Jackson 配置序列化为 ISO 字符串
 * （雪花 ID 不外露：本 VO 不含 id/updatedBy，避免无意义的 19 位长整型透传）。
 *
 * @param imageUrl  二维码图片地址（https 直链）；未配置时为 null（客户端渲染空态，而非报错）
 * @param updatedAt 最后更新时间；未配置时为 null
 */
public record GroupQrcodeVO(String imageUrl, LocalDateTime updatedAt) {
}
