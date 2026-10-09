package com.chiji.module.feedback.vo;

import java.util.List;

/**
 * 反馈对话线程 VO（用户端 feedback 为 {@link FeedbackVO}，管理端为 AdminFeedbackVO）。
 * <p>
 * 首条消息 = feedback 本体（content + 图片），其余消息在 comments 中按时间升序返回；
 * comments 取「最新一页」，nextCursor 指向更早一页，前端向上滚动加载历史。
 */
public record FeedbackThreadVO<T>(
        /** 反馈本体（首条消息 + 状态） */
        T feedback,
        /** 对话消息（评论，时间升序，不含 feedback 首条） */
        List<FeedbackCommentVO> comments,
        /** 下一页游标（加载更早消息时传入；无更多为 null） */
        Long nextCursor,
        /** 是否还有更早的消息 */
        Boolean hasMore) {
}
