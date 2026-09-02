package com.chiji.module.auth.dto;

/**
 * 登录响应体。
 * <p>
 * 返回 Sa-Token 颁发的 uuid 令牌与用户 ID。前端将 {@code token} 存入本地 storage，
 * 后续请求通过 {@code Authorization} 请求头携带。
 *
 * @param token  Sa-Token 令牌（uuid 风格，有效期 7 天）
 * @param userId 用户 ID（雪花算法生成，前端 Long 序列化为字符串）
 */
public record LoginResponse(String token, Long userId) {
}
