package com.chiji.framework.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理后台 P2 采集层配置属性。
 * <p>
 * 前缀 {@code chiji.metrics}：接口监控采集总开关、慢请求阈值、明细保留期。
 * {@code enabled=false} 时拦截器零开销跳过（线上异常一键止血）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "chiji.metrics")
public class MetricsProperties {

    /** 采集总开关（拦截器计时 + 阈值采集 + 计数器刷盘）。 */
    private boolean enabled = true;

    /** 慢请求阈值（ms），超过即写 slow_api_log 明细并计入慢请求数。 */
    private int slowThresholdMs = 500;

    /** slow_api_log 明细保留天数（超期物理删除）。 */
    private int slowLogRetentionDays = 90;

    /** login_log 保留天数。 */
    private int loginLogRetentionDays = 365;

    /** usage_session 保留天数。 */
    private int usageSessionRetentionDays = 180;
}
