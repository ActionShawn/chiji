package com.chiji.module.admin.service;

import com.chiji.common.core.page.CursorPage;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.vo.AdminFeedbackVO;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;

/**
 * 管理端反馈服务。
 * <p>
 * 管理员视角的反馈处理：全量查询、对话线程、追加评论。
 * 2026-10-08 对话化改造：闭环权归用户，运营不可单方关闭/重开（接口禁用，仅能回复）。
 * 与用户视角的 {@code FeedbackService} 隔离，避免职责混杂。
 */
public interface AdminFeedbackService {

    /**
     * 全量反馈分页查询（按 id 倒序，可按状态筛选）。
     *
     * @param status 处理状态筛选（WAIT_ADMIN/WAIT_USER/CLOSED，兼容旧值 PENDING/PROCESSED，null=全部）
     * @param limit  每页条数（1-50）
     * @param cursor 上一页最后一条 id（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<AdminFeedbackVO> list(String status, int limit, Long cursor);

    /**
     * 拉取反馈对话线程（反馈本体 + 最新一页评论），进入即清该反馈的管理侧未读。
     *
     * @param feedbackId 反馈 ID
     * @return 对话线程
     */
    FeedbackThreadVO<AdminFeedbackVO> thread(Long feedbackId);

    /**
     * 分页拉取评论（向历史加载），不触发已读。
     *
     * @param feedbackId 反馈 ID
     * @param limit      每页条数（1-50）
     * @param cursor     更早页游标（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<FeedbackCommentVO> comments(Long feedbackId, int limit, Long cursor);

    /**
     * 运营追加评论（正文/图片至少一项），状态流转为 WAIT_USER，并镜像写 reply 供历史版本客户端展示。
     * 已闭环（CLOSED）的反馈不可回复，需用户追问复活。
     *
     * @param feedbackId 反馈 ID
     * @param request    评论请求
     * @return 新消息 + 评论后状态
     */
    AddCommentResultVO addComment(Long feedbackId, AddCommentRequest request);

    /**
     * 回复反馈（历史接口兼容）：等价于 {@link #addComment} 的纯文本版本（不带图片）。
     *
     * @param feedbackId 反馈 ID
     * @param request    回复请求
     * @return 更新后的反馈 VO
     */
    AdminFeedbackVO reply(Long feedbackId, AdminReplyFeedbackRequest request);

    /**
     * 关闭反馈：已禁用（闭环权归用户，2026-10-08 对话化改造）。
     *
     * @param feedbackId 反馈 ID
     * @return 恒抛异常
     * @deprecated 运营不可单方关闭，保留签名仅为历史客户端路由兼容
     */
    @Deprecated
    AdminFeedbackVO close(Long feedbackId);

    /**
     * 重新打开反馈：已禁用（状态流转由用户评论/追问驱动，2026-10-08 对话化改造）。
     *
     * @param feedbackId 反馈 ID
     * @return 恒抛异常
     * @deprecated 运营不可干预状态，保留签名仅为历史客户端路由兼容
     */
    @Deprecated
    AdminFeedbackVO reopen(Long feedbackId);

    /**
     * 管理端未读数（管理入口数字角标）：未读的新反馈首条 + 用户新评论。
     *
     * @return 未读用户消息数
     */
    long unreadCount();
}
