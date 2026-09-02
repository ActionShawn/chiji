// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\storage\LocalStorageProperties.java
package com.chiji.framework.storage;

import lombok.Data;

/**
 * 本地存储策略专属配置。
 * <p>
 * 前缀 {@code chiji.storage.local}，作为 {@link StorageProperties#getLocal()} 的嵌套 POJO 自动绑定。
 */
@Data
public class LocalStorageProperties {

    /** 本地存储根目录，如 {@code D:/chiji/uploads}。 */
    private String basePath = "D:/chiji/uploads";

    /** 对外可访问 URL 前缀，如 {@code http://localhost:8080/uploads}。 */
    private String baseUrl = "http://localhost:8080/uploads";
}
