package com.chiji.module.admin.service;

import com.chiji.common.core.page.CursorPage;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.vo.AdminFeedbackVO;

/**
 * 管理端反馈服务。
 * <p>
 * 管理员视角的反馈处理：全量查询、回复（站内触达用户）、关闭/重开。
 * 与用户视角的 {@code FeedbackService} 隔离，避免职责混杂。
 */
public interface AdminFeedbackService {

    /**
     * 全量反馈分页查询（按 id 倒序，可按状态筛选）。
     *
     * @param status 处理状态筛选（PENDING/PROCESSED，null=全部）
     * @param limit  每页条数（1-50）
     * @param cursor 上一页最后一条 id（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<AdminFeedbackVO> list(String status, int limit, Long cursor);

    /**
     * 回复反馈：写入回复内容与时间，状态流转为已处理（PROCESSED）；可重复修改。
     *
     * @param feedbackId 反馈 ID
     * @param request    回复请求
     * @return 更新后的反馈 VO
     */
    AdminFeedbackVO reply(Long feedbackId, AdminReplyFeedbackRequest request);

    /**
     * 关闭反馈：标记为已处理（PROCESSED），不改动已有回复。
     *
     * @param feedbackId 反馈 ID
     * @return 更新后的反馈 VO
     */
    AdminFeedbackVO close(Long feedbackId);

    /**
     * 重新打开反馈：状态回退为待处理（PENDING），保留回复内容。
     *
     * @param feedbackId 反馈 ID
     * @return 更新后的反馈 VO
     */
    AdminFeedbackVO reopen(Long feedbackId);
}
