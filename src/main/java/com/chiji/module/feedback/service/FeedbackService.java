package com.chiji.module.feedback.service;

import com.chiji.common.core.page.CursorPage;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackStatusVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;
import com.chiji.module.feedback.vo.FeedbackVO;

/**
 * 意见反馈服务（用户端）。
 * <p>
 * 2026-10-08 对话化改造：反馈支持与运营连续评论式对话；闭环权在用户（点「已解决」，5 秒可撤回）；
 * 用户评论自动把状态切回「等回复」（CLOSED 时为追问复活）。
 */
public interface FeedbackService {

    /**
     * 提交意见（正文 + 图片 + 可选联系方式）。
     *
     * @param userId  当前登录用户 ID
     * @param request 提交请求
     * @return 保存后的反馈 VO
     */
    FeedbackVO submit(Long userId, SubmitFeedbackRequest request);

    /**
     * 分页查询当前用户的反馈历史（游标分页，按 id 倒序）。
     *
     * @param userId 当前登录用户 ID
     * @param limit  每页条数（1-50）
     * @param cursor 上一页最后一条 id（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<FeedbackVO> listByUser(Long userId, int limit, Long cursor);

    /**
     * 拉取反馈对话线程（反馈本体 + 最新一页评论），同时把该反馈的用户侧未读清零。
     *
     * @param userId     当前登录用户 ID（须为反馈归属人）
     * @param feedbackId 反馈 ID
     * @return 对话线程
     */
    FeedbackThreadVO<FeedbackVO> thread(Long userId, Long feedbackId);

    /**
     * 分页拉取对话评论（首页=最新一页升序，cursor 继续向历史加载），不触发已读。
     *
     * @param userId     当前登录用户 ID（须为反馈归属人）
     * @param feedbackId 反馈 ID
     * @param limit      每页条数（1-50）
     * @param cursor     更早页游标（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<FeedbackCommentVO> comments(Long userId, Long feedbackId, int limit, Long cursor);

    /**
     * 用户追加评论（正文/图片至少一项），状态流转为 WAIT_ADMIN（CLOSED 时为追问自动复活）。
     *
     * @param userId     当前登录用户 ID（须为反馈归属人）
     * @param feedbackId 反馈 ID
     * @param request    评论请求
     * @return 新消息 + 评论后状态
     */
    AddCommentResultVO addComment(Long userId, Long feedbackId, AddCommentRequest request);

    /**
     * 用户点「已解决」闭环：状态 → CLOSED 并记录闭环时刻（5 秒撤回窗口基准）。幂等。
     *
     * @param userId     当前登录用户 ID（须为反馈归属人）
     * @param feedbackId 反馈 ID
     * @return 闭环后状态（含撤回窗口剩余秒数）
     */
    FeedbackStatusVO resolve(Long userId, Long feedbackId);

    /**
     * 撤回闭环（仅闭环后 5 秒内）：回 WAIT_USER（有运营消息）或 WAIT_ADMIN（无运营消息）。
     *
     * @param userId     当前登录用户 ID（须为反馈归属人）
     * @param feedbackId 反馈 ID
     * @return 撤回后状态
     * @throws com.chiji.common.core.exception.BusinessException 超窗或非闭环态
     */
    FeedbackStatusVO revertResolve(Long userId, Long feedbackId);

    /**
     * 用户端未读数（运营在用户已读之后的新评论数；入口红点用，>0 即亮）。
     *
     * @param userId 当前登录用户 ID
     * @return 未读数
     */
    long unreadCount(Long userId);
}
