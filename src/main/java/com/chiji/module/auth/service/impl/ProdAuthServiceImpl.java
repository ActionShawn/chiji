package com.chiji.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.framework.wechat.WxLoginClient;
import com.chiji.framework.wechat.WxSession;
import com.chiji.module.auth.dto.LoginResponse;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * prod 环境认证服务实现（真实微信登录）。
 * <p>
 * 调用 {@link WxLoginClient#jscode2session(String)} 校验 code 换取 openid，
 * 再按 openid 查询或创建用户记录，最后颁发 Sa-Token。
 * 仅在非 dev profile（如 prod）下注册。
 */
@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class ProdAuthServiceImpl implements AuthService {

    private final WxLoginClient wxLoginClient;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "code 不能为空");
        }
        WxSession session = wxLoginClient.jscode2session(code);
        String openid = session.openid();

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            userMapper.insert(user);
            log.info("新用户注册, userId={}, openid={}", user.getId(), openid);
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        log.info("prod 登录成功, userId={}", user.getId());
        return new LoginResponse(token, user.getId());
    }
}
