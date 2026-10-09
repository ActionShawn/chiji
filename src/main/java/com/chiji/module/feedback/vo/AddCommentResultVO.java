package com.chiji.module.feedback.vo;

/**
 * 追加评论结果：新消息 + 评论后反馈状态（用户/运营共用，前端据 status 刷新状态条）。
 */
public record AddCommentResultVO(
        /** 新增的评论消息 */
        FeedbackCommentVO comment,
        /** 评论后状态：FeedbackStatusEnum.name() */
        String status,
        /** 状态中文标签 */
        String statusLabel) {
}
