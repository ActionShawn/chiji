package com.chiji.module.auth.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.dto.LoginRequest;
import com.chiji.module.auth.dto.LoginResponse;
import com.chiji.module.auth.service.AuthService;
import com.chiji.module.track.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * <p>
 * 已加入 Sa-Token 白名单（见 {@code SaTokenConfig}），免登录访问。
 */
@Tag(name = "认证", description = "登录与登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginLogService loginLogService;

    /**
     * 微信小程序登录。
     * <p>
     * dev 环境下 code 可空（测试用户模式）；prod 环境下 code 必填。
     * 登录成功后异步落 1 条登录日志（采集层，不阻塞响应）。
     *
     * @param request 登录请求体
     * @return 登录响应（token + userId）
     */
    @Operation(summary = "微信登录", description = "dev 环境固定登录测试用户；prod 环境调用微信 jscode2session")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.code());
        // 采集层埋点：controller 一处覆盖 dev/prod 两实现，异步不拉长事务
        loginLogService.recordAsync(response.userId());
        return R.ok(response);
    }
}
