package com.chiji.common.config;

import com.chiji.framework.metrics.ApiMetricsInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 接口监控拦截器注册。
 * <p>
 * {@link ApiMetricsInterceptor} 注册在最外层（order=-100，先于 SaTokenConfig 注册的
 * Sa-Token 拦截器），登录校验耗时计入接口耗时。
 */
@Configuration
@RequiredArgsConstructor
public class MetricsWebConfig implements WebMvcConfigurer {

    private final ApiMetricsInterceptor apiMetricsInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiMetricsInterceptor)
                .addPathPatterns("/**")
                .order(-100);
    }
}
