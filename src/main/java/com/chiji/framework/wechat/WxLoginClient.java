// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\wechat\WxLoginClient.java
package com.chiji.framework.wechat;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 微信登录客户端。
 * <p>
 * 基于 Spring 6 的 {@link RestClient} 调用微信 {@code jscode2session} 接口，
 * 使用小程序端 wx.login() 获取的临时凭证 code 换取用户身份信息。
 */
@Slf4j
@Service
public class WxLoginClient {

    /** 微信登录凭证校验接口主机。 */
    private static final String JSCODE2SESSION_HOST = "api.weixin.qq.com";

    private final WechatProperties wechatProperties;
    private final RestClient restClient;

    /**
     * 构造微信登录客户端。
     * <p>
     * 微信 {@code jscode2session} 接口偶发返回 {@code text/plain}（正常应为
     * {@code application/json}），默认的 {@link MappingJackson2HttpMessageConverter}
     * 不处理 {@code text/plain}，会导致 {@code UnknownContentTypeException}。
     * 这里基于全局 {@link RestClient.Builder} 构建专用客户端，为 Jackson converter
     * 追加 {@code text/plain} 支持，保证响应可正常反序列化为 {@link WxSession}。
     *
     * @param wechatProperties  微信小程序配置
     * @param restClientBuilder Spring Boot 自动配置的 RestClient 构建器
     * @param objectMapper      全局 ObjectMapper（与 JacksonConfig 定制一致）
     */
    public WxLoginClient(WechatProperties wechatProperties,
                         RestClient.Builder restClientBuilder,
                         ObjectMapper objectMapper) {
        this.wechatProperties = wechatProperties;
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        jsonConverter.setSupportedMediaTypes(
                List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        this.restClient = restClientBuilder
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(jsonConverter);
                })
                .build();
    }

    /**
     * 使用登录 code 换取微信会话信息。
     *
     * @param code 小程序端 wx.login() 获取的临时登录凭证
     * @return 微信会话信息
     * @throws BusinessException 微信接口返回错误码或调用异常时抛出 WECHAT_LOGIN_FAILED
     */
    public WxSession jscode2session(String code) {
        URI uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(JSCODE2SESSION_HOST)
                .path("/sns/jscode2session")
                .queryParam("appid", wechatProperties.getAppid())
                .queryParam("secret", wechatProperties.getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUri();

        WxSession session;
        try {
            session = restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(WxSession.class);
        } catch (Exception e) {
            log.error("调用微信 jscode2session 失败", e);
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED, "微信登录接口调用失败");
        }

        // 成功时微信仅返回 openid/session_key（不携带 errcode，缺失视为成功）；失败时 errcode 非 0
        if (session == null) {
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED, "微信接口返回为空");
        }
        if (session.errcode() != null && session.errcode() != 0) {
            log.warn("微信登录失败, errcode={}, errmsg={}", session.errcode(), session.errmsg());
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED, session.errmsg());
        }
        if (session.openid() == null || session.openid().isBlank()) {
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED, "微信接口返回异常");
        }
        return session;
    }
}
