// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\SaTokenConfig.java
package com.chiji.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 认证拦截器配置。
 * <p>
 * 拦截所有请求，仅对 {@code /api/**} 下的接口执行登录校验；
 * 白名单放行登录、基础自检与 AI 自检接口，并放行 CORS 预检请求（OPTIONS）。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match("/api/**")
                        .notMatch(SaHttpMethod.OPTIONS)
                        .notMatch("/api/auth/login", "/api/ping", "/api/ai/ping")
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }
}
