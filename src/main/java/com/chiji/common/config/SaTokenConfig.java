// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\SaTokenConfig.java
package com.chiji.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.module.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 认证拦截器配置。
 * <p>
 * 拦截所有请求，仅对 {@code /api/**} 下的接口执行登录校验；
 * 白名单放行登录、基础自检与 AI 自检接口，并放行 CORS 预检请求（OPTIONS）。
 * {@code /api/admin/**} 在登录校验之上叠加管理员角色校验（user.role = ADMIN）。
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final UserService userService;

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
                        // 开发者在线测试模块：路由本身由 chiji.dev-test.enabled 开关注册，仅联调用
                        .notMatch("/api/dev/**")
                        .check(r -> StpUtil.checkLogin())
                        // 管理端路由：登录之上叠加角色校验，非管理员返回 403
                        .match("/api/admin/**")
                        .check(r -> {
                            if (!userService.isAdmin(StpUtil.getLoginIdAsLong())) {
                                throw new BusinessException(ErrorCode.FORBIDDEN);
                            }
                        })))
                .addPathPatterns("/**");
    }
}
