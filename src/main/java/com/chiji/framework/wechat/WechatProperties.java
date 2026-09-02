// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\wechat\WechatProperties.java
package com.chiji.framework.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置属性。
 * <p>
 * 前缀 {@code chiji.wechat}，appid / secret 来自环境变量占位（见 application.yml）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.wechat")
public class WechatProperties {

    /** 小程序 AppID。 */
    private String appid;

    /** 小程序 AppSecret。 */
    private String secret;
}
