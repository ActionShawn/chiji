package com.chiji.module.feedback.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈评论 VO：对话流中的一条消息。
 */
public record FeedbackCommentVO(
        /** 评论 ID */
        Long id,
        /** 发言人角色：USER(用户)/ADMIN(运营) */
        String role,
        /** 评论正文（仅图片时为 null） */
        String content,
        /** 图片地址列表（无图片为空列表） */
        List<String> images,
        /** 发言时间 */
        LocalDateTime createdAt) {
}
