package com.chiji.framework.weather;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页天气接口。
 * <p>
 * 前端通过 {@code wx.getFuzzyLocation} 拿到模糊坐标后调用本接口，由服务端代理和风天气，
 * API Key 不下发客户端。接口在 Sa-Token 拦截范围内（需登录），见 {@code SaTokenConfig}。
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    /** 天气客户端。 */
    private final WeatherClient weatherClient;

    /**
     * 查询指定坐标的当日天气。
     *
     * @param lon 经度（模糊定位）
     * @param lat 纬度（模糊定位）
     * @return 城市 / 区县 / 天气文案 / 温度 / 图标 / 数据时间
     */
    @GetMapping
    public R<WeatherVO> weather(@RequestParam(required = false) Double lon,
                                @RequestParam(required = false) Double lat) {
        if (lon == null || lat == null
                || lon < -180 || lon > 180
                || lat < -90 || lat > 90) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "定位参数不合法");
        }
        return R.ok(weatherClient.fetch(lon, lat));
    }
}
