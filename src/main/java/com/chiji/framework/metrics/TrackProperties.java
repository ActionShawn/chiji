package com.chiji.framework.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 埋点上报配置属性。
 * <p>
 * 前缀 {@code chiji.track}：使用会话上报的防刷限频。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.track")
public class TrackProperties {

    /** 使用会话上报单用户每小时上限（正常前后台切换一天几十次，超限视为异常刷量） */
    private int sessionRateLimitPerHour = 30;
}
