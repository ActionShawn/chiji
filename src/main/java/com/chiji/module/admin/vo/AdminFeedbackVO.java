package com.chiji.module.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端反馈 VO。
 * <p>
 * 供管理后台反馈列表/详情展示：用户信息（昵称 + 脱敏 openid）、正文、图片、
 * 处理状态与回复。合规要求：不包含存量联系方式字段。
 */
public record AdminFeedbackVO(
        /** 反馈 ID */
        Long id,
        /** 提交用户 ID */
        Long userId,
        /** 提交用户昵称（未设置为 null，前端回退「微信用户」） */
        String nickname,
        /** 提交用户 openid（脱敏：前4后4，如 wx_o***xyz） */
        String openidMasked,
        /** 意见正文 */
        String content,
        /** 处理状态：FeedbackStatusEnum.name() */
        String status,
        /** 处理状态中文标签（PENDING=待处理 / PROCESSED=已处理） */
        String statusLabel,
        /** 管理员回复（未回复为 null） */
        String reply,
        /** 回复时间（未回复为 null） */
        LocalDateTime repliedAt,
        /** 图片地址列表（无图片为空列表） */
        List<String> images,
        /** 提交时间 */
        LocalDateTime createdAt) {
}
