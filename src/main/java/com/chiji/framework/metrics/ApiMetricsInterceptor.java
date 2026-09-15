package com.chiji.framework.metrics;

import cn.dev33.satoken.stp.StpUtil;
import com.chiji.module.track.service.SlowApiLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.TimeUnit;

/**
 * 接口监控拦截器。
 * <p>
 * 对 {@code /api/**} 计时（nanoTime，纳秒级开销）：内存计数器累加；超过慢阈值
 * 或失败的请求经 {@link SlowApiLogService} 异步写明细。排除埋点自身（/api/track/**）、
 * 健康检查与 OPTIONS 预检，避免数据自污染。
 * <p>
 * 注册顺序在最外层（order=-100，先于 Sa-Token），登录校验耗时计入接口耗时（合理口径）。
 * {@code chiji.metrics.enabled=false} 时零开销跳过。
 */
@Component
@RequiredArgsConstructor
public class ApiMetricsInterceptor implements HandlerInterceptor {

    /** 计时起点 request attribute key */
    static final String ATTR_T0 = "chiji.metrics.t0";

    private final MetricsProperties properties;
    private final ApiMetricsCollector collector;
    private final SlowApiLogService slowApiLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || !shouldTrack(request)) {
            return true;
        }
        request.setAttribute(ATTR_T0, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        Long t0 = (Long) request.getAttribute(ATTR_T0);
        if (t0 == null) {
            return;
        }
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        String api = resolveApi(request);
        boolean success = ex == null;
        boolean slow = costMs > properties.getSlowThresholdMs();
        collector.record(api, costMs, success, slow);

        // 阈值采集：慢请求或失败请求异步写明细，正常请求零落库
        if (slow || !success) {
            slowApiLogService.recordAsync(api, currentUserId(), costMs, success, ex);
        }
    }

    /** 是否纳入监控：仅 /api/**，排除埋点自身与健康检查。 */
    private boolean shouldTrack(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return false;
        }
        return !uri.startsWith("/api/track/")
                && !"/api/ping".equals(uri)
                && !"/api/ai/ping".equals(uri);
    }

    /**
     * 归一化接口模板：优先取 Spring 已匹配的路由模板（如 /api/feedback/{id}/reply），
     * 计数器 key 不因路径参数膨胀；拿不到时回退到原始 URI 截断。
     */
    private String resolveApi(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isBlank()) {
            return s;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.length() > 190 ? uri.substring(0, 190) : uri;
    }

    /** 当前登录用户 ID（未登录/异常返回 null，不抛出影响统计）。 */
    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
