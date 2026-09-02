// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\storage\StorageProperties.java
package com.chiji.framework.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置。
 * <p>
 * 前缀 {@code chiji.storage}，通过 {@code type} 切换策略实现：
 * local（默认，本地磁盘）/ cos（腾讯云 COS）。各策略专属配置放在对应子 POJO。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.storage")
public class StorageProperties {

    /** 存储类型：local（默认）/ cos */
    private String type = "local";

    /** 本地策略配置 */
    private LocalStorageProperties local = new LocalStorageProperties();

    /** 腾讯云 COS 策略配置 */
    private CosStorageProperties cos = new CosStorageProperties();
}
