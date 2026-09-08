package com.chiji.module.message.service;

import com.chiji.module.message.vo.MessagePageVO;

/**
 * 信箱消息服务（用户侧读取/已读/删除）。
 * <p>
 * 消息的写入由 {@link ReminderNotifyService} 统一入口（按通知开关门控），本服务只读。
 */
public interface MessageService {

    /**
     * 分页查询信箱消息（按 id 倒序，最新在前）。
     * <p>
     * 兼容存量消息（reminderType 为 null）与佩戴功能新消息；category 可按分类过滤
     * （SYSTEM_CARE / WEAR / …），缺省返回全部。返回的 {@code unreadCount} 为当前筛选范围内的未读数。
     *
     * @param userId   当前用户 ID
     * @param cursor   上一页游标（首页为 null，取 id 小于该值的下一页）
     * @param pageSize 每页条数（缺省 20，上限 50）
     * @param category 分类过滤（可空）
     * @return 信箱分页 VO
     */
    MessagePageVO listMessages(Long userId, Long cursor, Integer pageSize, String category);

    /**
     * 统计信箱未读数（全量，不分分类）。
     *
     * @param userId 当前用户 ID
     * @return 未读数
     */
    long countUnread(Long userId);

    /**
     * 标记单条消息已读（归属校验，非本人消息视为不存在）。
     */
    void markRead(Long userId, Long messageId);

    /**
     * 全部标为已读。
     */
    void markAllRead(Long userId);

    /**
     * 逻辑删除单条消息（归属校验）。
     */
    void deleteMessage(Long userId, Long messageId);
}
