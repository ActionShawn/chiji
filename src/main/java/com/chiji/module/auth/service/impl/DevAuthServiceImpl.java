package com.chiji.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.module.auth.dto.LoginResponse;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * dev 环境认证服务实现（测试用户模式）。
 * <p>
 * 无论 code 是否传入，均固定登录种子数据中的 dev 用户（id=1），
 * 直接调用 {@link StpUtil#login(Object)} 颁发 token，不校验微信凭证。
 * 仅在 {@code dev} profile 下注册，避免测试逻辑泄漏到生产。
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevAuthServiceImpl implements AuthService {

    /** dev 模式固定登录的种子用户 ID（与 seed.sql 中的 user.id=1 对齐）。 */
    private static final long DEV_USER_ID = 1L;

    private final UserMapper userMapper;

    @Override
    public LoginResponse login(String code) {
        User user = userMapper.selectById(DEV_USER_ID);
        if (user == null) {
            log.error("dev 用户不存在, userId={}", DEV_USER_ID);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        StpUtil.login(DEV_USER_ID);
        String token = StpUtil.getTokenValue();
        log.info("dev 登录成功, userId={}, nickname={}", user.getId(), user.getNickname());
        return new LoginResponse(token, user.getId());
    }
}
