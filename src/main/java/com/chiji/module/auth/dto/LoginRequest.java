package com.chiji.module.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 微信登录请求体。
 * <p>
 * {@code code} 为小程序端 {@code wx.login()} 获取的临时登录凭证。
 * dev profile 下允许为空（测试用户模式），prod profile 下必填。
 *
 * @param code 微信登录凭证（dev 可空）
 */
public record LoginRequest(
        @Size(max = 128, message = "code 长度不能超过 128") String code
) {
}
