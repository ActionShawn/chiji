package com.chiji.framework.wechat;

import com.chiji.common.util.RedisService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信一次性订阅消息客户端。
 * <p>
 * 负责两件事：
 * <ol>
 *   <li>获取并缓存小程序全局 {@code access_token}（微信不提供剩余订阅额度查询接口，额度由本地记账）；</li>
 *   <li>下发一次性订阅消息（用户授权一次可下发一条，不限时间）。</li>
 * </ol>
 * access_token 缓存于 Redis（多实例共享），过期时间取 {@code expires_in - 300s} 以提前刷新。
 */
@Slf4j
@Service
public class WxSubscribeClient {

    /** 微信接口主机。 */
    private static final String WECHAT_HOST = "api.weixin.qq.com";

    /** access_token 的 Redis 缓存键。 */
    private static final String ACCESS_TOKEN_KEY = "chiji:wx:access_token";

    /** access_token 提前刷新余量（秒）。 */
    private static final long REFRESH_AHEAD_SECONDS = 300L;

    private final WechatProperties wechatProperties;
    private final RedisService redisService;
    private final RestClient restClient;

    /**
     * 构造微信订阅消息客户端。
     * <p>
     * 复用 {@link WxLoginClient} 的 converter 处理方式：微信接口偶发返回
     * {@code text/plain}，需为 Jackson converter 追加该媒体类型支持。
     *
     * @param wechatProperties  微信小程序配置
     * @param restClientBuilder Spring Boot 自动配置的 RestClient 构建器
     * @param objectMapper      全局 ObjectMapper
     * @param redisService      Redis 通用操作服务（access_token 缓存）
     */
    public WxSubscribeClient(WechatProperties wechatProperties,
                             RestClient.Builder restClientBuilder,
                             ObjectMapper objectMapper,
                             RedisService redisService) {
        this.wechatProperties = wechatProperties;
        this.redisService = redisService;
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
     * 订阅消息下发是否可用：开关开启且模板 ID 已配置。
     *
     * @return 可用返回 true
     */
    public boolean isEnabled() {
        WechatProperties.Subscribe subscribe = wechatProperties.getSubscribe();
        return subscribe != null
                && subscribe.isEnabled()
                && subscribe.getAlignerChangeTemplateId() != null
                && !subscribe.getAlignerChangeTemplateId().isBlank();
    }

    /**
     * 下发换副提醒订阅消息。
     *
     * @param openid 用户 openid
     * @param data   模板数据（须覆盖模板定义的全部关键词，否则微信返回 47003）
     * @return 微信返回 errcode == 0（下发成功）返回 true；否则记录日志并返回 false
     */
    public boolean sendAlignerChangeMessage(String openid, Map<String, Object> data) {
        if (openid == null || openid.isBlank()) {
            return false;
        }
        WechatProperties.Subscribe subscribe = wechatProperties.getSubscribe();
        String token;
        try {
            token = getAccessToken();
        } catch (Exception e) {
            log.error("获取微信 access_token 失败", e);
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", subscribe.getAlignerChangeTemplateId());
        body.put("page", subscribe.getPage());
        body.put("miniprogram_state", subscribe.getMiniprogramState());
        body.put("lang", "zh_CN");
        body.put("data", data);

        URI uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(WECHAT_HOST)
                .path("/cgi-bin/message/subscribe/send")
                .queryParam("access_token", token)
                .build()
                .toUri();

        WxSubscribeResponse response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(WxSubscribeResponse.class);
        } catch (Exception e) {
            log.error("下发微信订阅消息异常, openid={}", openid, e);
            return false;
        }
        if (response == null) {
            log.warn("下发微信订阅消息返回为空, openid={}", openid);
            return false;
        }
        if (response.errcode() != null && response.errcode() != 0) {
            log.warn("下发微信订阅消息失败, openid={}, errcode={}, errmsg={}",
                    openid, response.errcode(), response.errmsg());
            return false;
        }
        return true;
    }

    /**
     * 获取小程序全局 access_token（优先读 Redis 缓存）。
     * <p>
     * 微信 access_token 有效期 7200s 且获取会相互顶掉，故缓存过期时间取
     * {@code expires_in - 300s}；多实例部署必须共享缓存，不能使用本地内存。
     *
     * @return access_token；获取失败返回 null
     */
    private String getAccessToken() {
        String cached = redisService.get(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        URI uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(WECHAT_HOST)
                .path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", wechatProperties.getAppid())
                .queryParam("secret", wechatProperties.getSecret())
                .build()
                .toUri();

        WxAccessTokenResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(WxAccessTokenResponse.class);
        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            log.warn("获取微信 access_token 失败, errcode={}, errmsg={}",
                    response == null ? null : response.errcode(),
                    response == null ? null : response.errmsg());
            return null;
        }
        long expiresIn = response.expires_in() == null ? 7200L : response.expires_in();
        long ttl = Math.max(expiresIn - REFRESH_AHEAD_SECONDS, 60L);
        redisService.set(ACCESS_TOKEN_KEY, response.access_token(), Duration.ofSeconds(ttl));
        return response.access_token();
    }

    /**
     * 微信 access_token 响应。
     *
     * @param access_token 接口调用凭证
     * @param expires_in   凭证有效期（秒）
     * @param errcode      错误码（成功时为 0 或缺失）
     * @param errmsg       错误信息
     */
    private record WxAccessTokenResponse(String access_token,
                                         Integer expires_in,
                                         Integer errcode,
                                         String errmsg) {
    }

    /**
     * 微信订阅消息下发响应。
     *
     * @param errcode 错误码（0 表示成功）
     * @param errmsg  错误信息
     */
    private record WxSubscribeResponse(@JsonProperty("errcode") Integer errcode,
                                       @JsonProperty("errmsg") String errmsg) {
    }
}
