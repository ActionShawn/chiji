package com.chiji.module.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.FeedbackComment;
import com.chiji.module.feedback.mapper.FeedbackCommentMapper;
import com.chiji.module.feedback.support.FeedbackCommentSupport;
import com.chiji.module.feedback.vo.FeedbackCommentVO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评论分页加载器（用户端/管理端共用，避免两处实现漂移）。
 * <p>
 * 置于 service 包而非 support 包：ArchUnit 守护规则要求 mapper 仅能被 service 层类访问。
 */
public final class FeedbackCommentLoader {

    private FeedbackCommentLoader() {
    }

    /**
     * 对话消息分页：取「最新一页」按时间升序返回，nextCursor 指向更早一页。
     *
     * @param mapper     评论 Mapper
     * @param feedbackId 反馈 ID
     * @param limit      每页条数（1-50）
     * @param cursor     更早页游标（首页传 null）
     * @return 游标分页结果
     */
    public static CursorPage<FeedbackCommentVO> loadPage(FeedbackCommentMapper mapper, Long feedbackId,
                                                         int limit, Long cursor) {
        int pageSize = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<FeedbackComment> wrapper = new LambdaQueryWrapper<FeedbackComment>()
                .eq(FeedbackComment::getFeedbackId, feedbackId)
                .orderByDesc(FeedbackComment::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursor != null) {
            wrapper.lt(FeedbackComment::getId, cursor);
        }

        List<FeedbackComment> rows = mapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<FeedbackComment> page = hasMore ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        List<FeedbackCommentVO> items = page.stream()
                .map(FeedbackCommentSupport::toVO)
                .collect(Collectors.toList());
        Collections.reverse(items);
        return new CursorPage<>(items, nextCursor, hasMore);
    }
}
