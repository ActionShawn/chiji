package com.chiji.module.message.vo;

/**
 * 信箱未读数 VO。
 *
 * @param unread 未读消息总数（计数必为小整数：用 Integer 而非 Long，
 *               全局 Jackson 把 Long 序列化为字符串，前端 typeof === 'number' 判定会挂）
 */
public record UnreadVO(Integer unread) {
}
