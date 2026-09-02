package com.chiji.module.auth.service;

import com.chiji.module.auth.dto.LoginResponse;

/**
 * 认证服务接口。
 * <p>
 * 区分 dev / prod 两套实现：
 * <ul>
 *     <li>dev：测试用户模式，固定登录 userId=1，无需真实微信 code；</li>
 *     <li>prod：调用微信 {@code jscode2session} 校验 code 并创建/更新用户。</li>
 * </ul>
 */
public interface AuthService {

    /**
     * 登录。
     *
     * @param code 微信登录凭证（dev 可空，prod 必填）
     * @return 登录响应（token + userId）
     */
    LoginResponse login(String code);
}
