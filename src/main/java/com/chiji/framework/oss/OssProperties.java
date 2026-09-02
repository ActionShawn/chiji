// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\oss\OssProperties.java
package com.chiji.framework.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性。
 * <p>
 * 前缀 {@code chiji.oss}，各字段的值来自环境变量占位（见 application.yml），
 * 未配置时不阻塞应用启动，首次使用时校验。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.oss")
public class OssProperties {

    /** Endpoint，如 {@code oss-cn-hangzhou.aliyuncs.com}。 */
    private String endpoint;

    /** Bucket 名称。 */
    private String bucket;

    /** AccessKeyId。 */
    private String accessKeyId;

    /** AccessKeySecret。 */
    private String accessKeySecret;
}
