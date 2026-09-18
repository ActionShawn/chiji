package com.chiji.framework.weather;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 和风天气配置属性。
 * <p>
 * 前缀 {@code chiji.weather}：专属 API Host 与 API Key 属凭据，禁止写默认明文，
 * 由环境变量 WEATHER_API_HOST / WEATHER_API_KEY 注入；未注入时天气接口直接降级，不影响启动。
 * 另含缓存时长与免费额度兜底两组可调参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.weather")
public class WeatherProperties {

    /** 天气功能总开关（false 时不调三方，接口直接返回降级错误码）。 */
    private boolean enabled = true;

    /** 专属 API Host（和风控制台「项目」分配，形如 abcxyz.qweatherapi.com，不含协议）。 */
    private String host;

    /** API Key（和风控制台「项目」领取，仅存服务端、不下发客户端）。 */
    private String key;

    /** 缓存策略。 */
    private Cache cache = new Cache();

    /** 免费额度兜底。 */
    private Quota quota = new Quota();

    /**
     * 配置是否可用：开关开启且 Host / Key 均已注入。
     *
     * @return 可用返回 true
     */
    public boolean configured() {
        return enabled
                && host != null && !host.isBlank()
                && key != null && !key.isBlank();
    }

    /**
     * 缓存策略。
     * <p>
     * 实时天气按「城市 + 区县 + 小时」共享：同区县所有用户在缓存期内只打一次三方；
     * 城市反查按「取整坐标（2 位小数）」共享：区县边界长期稳定，可放长 TTL。
     */
    @Data
    public static class Cache {

        /** 实时天气缓存时长（分钟）。 */
        private long nowTtlMinutes = 60;

        /** 城市反查缓存时长（分钟），默认 24h。 */
        private long geoTtlMinutes = 1440;
    }

    /**
     * 免费额度兜底。
     * <p>
     * 和风「天气和基础服务」每自然月前 {@code monthlyLimit} 次免费，
     * 月调用量达到 {@code monthlyLimit × safetyRatio} 即熔断：停止调三方、返回降级错误码，
     * 由前端回显本地旧值并标注时间，避免超额产生费用。
     */
    @Data
    public static class Quota {

        /** 每自然月免费调用上限（次）。 */
        private long monthlyLimit = 50000;

        /** 安全水位比例（0.9 = 45000 次触发熔断，留余量应对月度内突发）。 */
        private double safetyRatio = 0.9;

        /**
         * 熔断阈值 = 月免费额度 × 安全水位。
         *
         * @return 触发熔断的月调用量
         */
        public long threshold() {
            return (long) (monthlyLimit * safetyRatio);
        }
    }
}
