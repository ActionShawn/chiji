package com.chiji.framework.weather;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.util.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气客户端（首页天气唯一出网口）。
 * <p>
 * 职责与顺序：
 * <ol>
 *   <li><b>城市反查</b>：模糊定位坐标 → 区县 + 所属市（按取整坐标 2 位小数缓存，默认 24h）；</li>
 *   <li><b>实时天气</b>：按 LocationID 查询当前天气（按「城市 + 区县 + 小时」缓存，默认 60min，
 *       同区县所有用户在缓存期内只打一次三方）；</li>
 *   <li><b>免费额度兜底</b>：每次真实出网前先原子自增当月计数，超过
 *       {@link WeatherProperties.Quota#threshold()}（默认 45000 = 50000 × 0.9）即熔断，
 *       抛 {@link ErrorCode#WEATHER_QUOTA_EXCEEDED} 且不再发起三方请求，避免超额计费。</li>
 * </ol>
 * 命中缓存的请求不计入额度，也不出网。Host / Key 未注入时抛
 * {@link ErrorCode#WEATHER_NOT_CONFIGURED}，不影响应用启动。
 */
@Slf4j
@Service
public class WeatherClient {

    /** 和风接口时区（东八区）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 小时粒度格式化（缓存键）。 */
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /** 自然月格式化（额度计数键）。 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 对外展示时间格式化。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 城市反查缓存键前缀。 */
    private static final String GEO_KEY_PREFIX = "chiji:weather:geo:";

    /** 实时天气缓存键前缀。 */
    private static final String NOW_KEY_PREFIX = "chiji:weather:now:";

    /** 月度调用量计数键前缀。 */
    private static final String QUOTA_KEY_PREFIX = "chiji:weather:quota:";

    /** 和风 API Key 请求头名。 */
    private static final String API_KEY_HEADER = "X-QW-Api-Key";

    /** 和风成功响应码。 */
    private static final String SUCCESS_CODE = "200";

    private final WeatherProperties weatherProperties;
    private final RedisService redisService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造天气客户端。
     * <p>
     * 响应统一以字节流接收、手动解压后经 Jackson 解析（见 {@link #fetchJson}），
     * 不依赖 message converter，三方返回 {@code text/plain} 等非 JSON Content-Type 也能正常承接。
     *
     * @param weatherProperties 和风天气配置
     * @param restClientBuilder Spring Boot 自动配置的 RestClient 构建器
     * @param objectMapper      全局 ObjectMapper
     * @param redisService      Redis 通用操作服务（缓存 + 额度计数）
     */
    public WeatherClient(WeatherProperties weatherProperties,
                         RestClient.Builder restClientBuilder,
                         ObjectMapper objectMapper,
                         RedisService redisService) {
        this.weatherProperties = weatherProperties;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    /**
     * 查询指定坐标的实时天气。
     *
     * @param lon 经度（模糊定位，GCJ-02 与 WGS-84 差异对市/区级反查无影响）
     * @param lat 纬度
     * @return 首页展示用天气数据
     * @throws BusinessException 未配置（60007）/ 额度熔断（60008）/ 调用失败（60009）
     */
    public WeatherVO fetch(double lon, double lat) {
        if (!weatherProperties.configured()) {
            throw new BusinessException(ErrorCode.WEATHER_NOT_CONFIGURED);
        }
        GeoLocation geo = locate(lon, lat);
        String cacheKey = NOW_KEY_PREFIX + geo.adm2() + ":" + geo.name() + ":"
                + HOUR_FORMATTER.format(ZonedDateTime.now(ZONE));
        WeatherVO cached = redisService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        checkQuota();
        WeatherNowResponse response = requestNow(geo.id());
        if (response == null || !SUCCESS_CODE.equals(response.code()) || response.now() == null) {
            log.warn("和风实时天气返回异常, locationId={}, code={}",
                    geo.id(), response == null ? null : response.code());
            throw new BusinessException(ErrorCode.WEATHER_CALL_FAILED);
        }
        WeatherVO vo = new WeatherVO(
                geo.adm2(),
                geo.name(),
                response.now().text(),
                response.now().temp(),
                response.now().icon(),
                TIME_FORMATTER.format(LocalDateTime.now(ZONE)));
        redisService.set(cacheKey, vo, Duration.ofMinutes(weatherProperties.getCache().getNowTtlMinutes()));
        return vo;
    }

    /**
     * 坐标反查城市/区县（优先读缓存）。
     *
     * @param lon 经度
     * @param lat 纬度
     * @return 反查结果
     */
    private GeoLocation locate(double lon, double lat) {
        String cacheKey = GEO_KEY_PREFIX + String.format(Locale.ROOT, "%.2f,%.2f", lon, lat);
        GeoLocation cached = redisService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        checkQuota();
        GeoResponse response = requestGeo(lon, lat);
        if (response == null || !SUCCESS_CODE.equals(response.code())
                || response.location() == null || response.location().isEmpty()) {
            log.warn("和风城市反查返回异常, code={}, size={}",
                    response == null ? null : response.code(),
                    response == null || response.location() == null ? 0 : response.location().size());
            throw new BusinessException(ErrorCode.WEATHER_CALL_FAILED);
        }
        GeoLocation location = response.location().get(0);
        redisService.set(cacheKey, location,
                Duration.ofMinutes(weatherProperties.getCache().getGeoTtlMinutes()));
        return location;
    }

    /**
     * 免费额度兜底：出网前原子自增当月调用量，超过安全水位即熔断。
     * <p>
     * 必须在发起三方请求<b>之前</b>判断，否则请求已发出即已产生计费。
     * Redis 异常时自增返回 0，按「未超限」放行（缓存与熔断均属保护措施，故障时不影响主流程）。
     */
    private void checkQuota() {
        long threshold = weatherProperties.getQuota().threshold();
        String key = QUOTA_KEY_PREFIX + MONTH_FORMATTER.format(ZonedDateTime.now(ZONE));
        long count = redisService.increment(key, quotaTtl());
        if (count > threshold) {
            log.warn("和风天气月度调用量已达熔断阈值, key={}, count={}, threshold={}", key, count, threshold);
            throw new BusinessException(ErrorCode.WEATHER_QUOTA_EXCEEDED);
        }
    }

    /**
     * 额度计数键的过期时长：距下月 1 日 0 点的秒数（自然月滚动重置）。
     *
     * @return 过期时长
     */
    private Duration quotaTtl() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime nextMonth = now.withDayOfMonth(1).plusMonths(1).truncatedTo(ChronoUnit.DAYS);
        return Duration.ofSeconds(Math.max(Duration.between(now, nextMonth).getSeconds(), 60L));
    }

    /**
     * 调用城市反查接口。
     *
     * @param lon 经度
     * @param lat 纬度
     * @return 响应体；网络异常时抛 {@link ErrorCode#WEATHER_CALL_FAILED}
     */
    private GeoResponse requestGeo(double lon, double lat) {
        // 和风要求坐标中的逗号 URL 编码为 %2C
        String location = URLEncoder.encode(
                String.format(Locale.ROOT, "%.2f,%.2f", lon, lat), StandardCharsets.UTF_8);
        URI uri = buildUri("/geo/v2/city/lookup", "location", location);
        try {
            return fetchJson(uri, GeoResponse.class);
        } catch (Exception e) {
            log.error("和风城市反查调用失败, lon={}, lat={}", lon, lat, e);
            throw new BusinessException(ErrorCode.WEATHER_CALL_FAILED);
        }
    }

    /**
     * 调用实时天气接口。
     *
     * @param locationId 和风 LocationID
     * @return 响应体；网络异常时抛 {@link ErrorCode#WEATHER_CALL_FAILED}
     */
    private WeatherNowResponse requestNow(String locationId) {
        URI uri = buildUri("/v7/weather/now", "location", locationId);
        try {
            return fetchJson(uri, WeatherNowResponse.class);
        } catch (Exception e) {
            log.error("和风实时天气调用失败, locationId={}", locationId, e);
            throw new BusinessException(ErrorCode.WEATHER_CALL_FAILED);
        }
    }

    /**
     * 请求和风接口并解析 JSON 响应。
     * <p>
     * 和风所有接口响应默认 Gzip 压缩，而 RestClient 默认请求工厂不自动解压，
     * 直接经 Jackson converter 解析会因首字节 0x1F（gzip 魔数）触发
     * {@code JsonParseException: Illegal character (CTRL-CHAR, code 31)}。
     * 这里显式声明 {@code Accept-Encoding: gzip}，按响应头与魔数识别压缩体后
     * 手动解压，未压缩时按原字节解析，兼容请求工厂已自动解压的场景。
     *
     * @param uri       请求 URI
     * @param valueType 目标类型
     * @param <T>       响应类型
     * @return 解析后的响应对象
     * @throws IOException 网络或解压/解析失败
     */
    private <T> T fetchJson(URI uri, Class<T> valueType) throws IOException {
        byte[] body = restClient.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .header(API_KEY_HEADER, weatherProperties.getKey())
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            return null;
        }
        if (body[0] == (byte) 0x1f && body[1] == (byte) 0x8b) {
            try (ByteArrayInputStream bin = new ByteArrayInputStream(body);
                 GZIPInputStream gin = new GZIPInputStream(bin)) {
                return objectMapper.readValue(gin, valueType);
            }
        }
        return objectMapper.readValue(body, valueType);
    }

    /**
     * 构造和风接口 URI（Host 取配置的专属域名）。
     *
     * @param path         接口路径
     * @param paramName    查询参数名
     * @param encodedValue 已 URL 编码的查询参数值
     * @return 请求 URI
     */
    private URI buildUri(String path, String paramName, String encodedValue) {
        return UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(weatherProperties.getHost())
                .path(path)
                .queryParam(paramName, encodedValue)
                .build(true)
                .toUri();
    }

    /**
     * 城市反查结果（可缓存）。
     *
     * @param id   LocationID（实时天气查询入参）
     * @param name 区县名（如「包河区」）
     * @param adm2 上级市名（如「合肥市」）
     */
    public record GeoLocation(String id, String name, String adm2) {
    }

    /**
     * 城市反查响应（仅承接所需字段，其余字段由 failOnUnknownProperties=false 忽略）。
     *
     * @param code     响应码（200 表示成功）
     * @param location 匹配的地点列表（按相关度排序，取首个）
     */
    private record GeoResponse(String code, List<GeoLocation> location) {
    }

    /**
     * 实时天气响应。
     *
     * @param code 响应码（200 表示成功）
     * @param now  实况数据
     */
    private record WeatherNowResponse(String code, Now now) {
    }

    /**
     * 实况数据（仅承接展示所需字段）。
     *
     * @param text 天气文案（如「晴」）
     * @param temp 温度（摄氏度字符串）
     * @param icon 图标代码（对应前端 assets/weather/{icon}.svg）
     */
    private record Now(String text, String temp, String icon) {
    }
}
