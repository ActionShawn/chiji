package com.chiji.framework.storage;

import lombok.Data;

/**
 * 腾讯云 COS 存储策略专属配置。
 * <p>
 * 前缀 {@code chiji.storage.cos}，作为 {@link StorageProperties#getCos()} 的嵌套 POJO 自动绑定。
 */
@Data
public class CosStorageProperties {

    /** SecretId（从腾讯云访问管理 CAM 获取）。 */
    private String secretId;

    /** SecretKey。 */
    private String secretKey;

    /** 存储桶地域，如 {@code ap-shanghai}（须与云托管同地域以获得内网互通）。 */
    private String region = "ap-shanghai";

    /** 存储桶名称，如 {@code chiji-1250000000}。 */
    private String bucket;

    /** 对外可访问 URL 前缀（Bucket 公网访问域名或 CDN 加速域名），如 {@code https://xxx.cos.ap-shanghai.myqcloud.com}。 */
    private String baseUrl;
}
