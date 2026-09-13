// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\wechat\WechatProperties.java
package com.chiji.framework.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置属性。
 * <p>
 * 前缀 {@code chiji.wechat}，appid / secret 来自环境变量占位（见 application.yml）；
 * 一次性订阅消息（换副提醒）的模板与跳转配置见 {@link Subscribe}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.wechat")
public class WechatProperties {

    /** 小程序 AppID。 */
    private String appid;

    /** 小程序 AppSecret。 */
    private String secret;

    /** 一次性订阅消息配置（换副提醒）。 */
    private Subscribe subscribe = new Subscribe();

    /**
     * 一次性订阅消息配置。
     * <p>
     * 个人主体小程序只能申请一次性订阅消息：用户授权一次可下发一条，不限时间；
     * 模板 ID 非机密，允许配置默认值。
     */
    @Data
    public static class Subscribe {

        /** 是否启用订阅消息下发（false 时仅站内消息，便于灰度/回滚）。 */
        private boolean enabled = true;

        /** 换副提醒模板 ID。 */
        private String alignerChangeTemplateId;

        /** 点击模板消息的跳转页。 */
        private String page = "pages/index/index";

        /** 跳转小程序类型：formal(正式版)/trial(体验版)/developer(开发版)。 */
        private String miniprogramState = "formal";
    }
}
