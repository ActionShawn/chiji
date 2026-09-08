package com.chiji.module.message.vo;

import java.util.List;

/**
 * 信箱分页返回 VO。
 *
 * @param items       消息列表（按 id 倒序，最新在前）
 * @param nextCursor  下一页游标（无更多为 null）
 * @param hasMore     是否还有下一页
 * @param unreadCount 当前筛选范围内的未读数（前端角标 / 空态提示）
 */
public record MessagePageVO(
        List<MessageVO> items,
        Long nextCursor,
        Boolean hasMore,
        Integer unreadCount
) {
}
