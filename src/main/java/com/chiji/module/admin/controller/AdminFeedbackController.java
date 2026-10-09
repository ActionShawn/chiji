package com.chiji.module.admin.controller;

import com.chiji.common.core.page.CursorPage;
import com.chiji.common.core.result.R;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.service.AdminFeedbackService;
import com.chiji.module.admin.vo.AdminFeedbackVO;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端反馈接口。
 * <p>
 * 受 Sa-Token 登录校验 + 管理员角色校验双重保护（见 SaTokenConfig），
 * 非管理员访问返回 403。用户信息（昵称/脱敏 openid）随列表返回。
 * 2026-10-08 对话化改造：支持连续对话；关闭/重开已禁用（闭环权归用户）。
 */
@Tag(name = "管理端-反馈", description = "意见反馈的管理员对话处理")
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    /**
     * 全量反馈分页查询（游标分页，按提交时间倒序）。
     *
     * @param status 状态筛选（WAIT_ADMIN/WAIT_USER/CLOSED，兼容旧值 PENDING/PROCESSED，不传=全部）
     * @param limit  每页条数（默认 20，最大 50）
     * @param cursor 上一页最后一条 id（首页不传）
     * @return 游标分页结果
     */
    @Operation(summary = "反馈列表", description = "全量反馈游标分页，可按状态筛选（兼容历史筛选值）")
    @GetMapping("/list")
    public R<CursorPage<AdminFeedbackVO>> list(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @RequestParam(required = false) Long cursor) {
        return R.ok(adminFeedbackService.list(status, limit, cursor));
    }

    /**
     * 拉取反馈对话线程（反馈本体 + 最新一页评论），进入即清该反馈的管理侧未读。
     *
     * @param feedbackId 反馈 ID
     * @return 对话线程
     */
    @Operation(summary = "拉取对话线程", description = "首条=反馈本体；评论取最新一页升序；进入即清未读")
    @GetMapping("/{feedbackId}/thread")
    public R<FeedbackThreadVO<AdminFeedbackVO>> thread(@PathVariable Long feedbackId) {
        return R.ok(adminFeedbackService.thread(feedbackId));
    }

    /**
     * 分页拉取评论（向历史加载），不触发已读。
     *
     * @param feedbackId 反馈 ID
     * @param limit      每页条数（默认 20，最大 50）
     * @param cursor     更早页游标（首页不传）
     * @return 游标分页结果
     */
    @Operation(summary = "分页拉取评论", description = "cursor 传上一页 nextLastId 继续向历史加载")
    @GetMapping("/{feedbackId}/comments")
    public R<CursorPage<FeedbackCommentVO>> comments(@PathVariable Long feedbackId,
                                                     @RequestParam(defaultValue = "20") int limit,
                                                     @RequestParam(required = false) Long cursor) {
        return R.ok(adminFeedbackService.comments(feedbackId, limit, cursor));
    }

    /**
     * 运营追加评论（正文/图片至少一项，允许仅图片）。
     *
     * @param feedbackId 反馈 ID
     * @param request    评论请求
     * @return 新消息 + 评论后状态
     */
    @Operation(summary = "追加评论", description = "允许仅图片；已闭环反馈不可回复，需用户追问复活")
    @PostMapping("/{feedbackId}/comments")
    public R<AddCommentResultVO> addComment(@PathVariable Long feedbackId,
                                            @Valid @RequestBody AddCommentRequest request) {
        return R.ok(adminFeedbackService.addComment(feedbackId, request));
    }

    /**
     * 回复反馈（历史接口兼容，等价纯文本追加评论）。
     *
     * @param feedbackId 反馈 ID
     * @param request    回复请求
     * @return 更新后的反馈 VO
     */
    @Operation(summary = "回复反馈（兼容）", description = "历史客户端兼容：写入一条 ADMIN 评论并镜像 reply")
    @PostMapping("/{feedbackId}/reply")
    public R<AdminFeedbackVO> reply(@PathVariable Long feedbackId,
                                    @Valid @RequestBody AdminReplyFeedbackRequest request) {
        return R.ok(adminFeedbackService.reply(feedbackId, request), "已回复");
    }

    /**
     * 关闭反馈：已禁用。
     *
     * @param feedbackId 反馈 ID
     * @return 恒返回 403
     */
    @Operation(summary = "关闭反馈（已禁用）", description = "闭环由用户点「已解决」完成，运营不可单方关闭")
    @PostMapping("/{feedbackId}/close")
    public R<AdminFeedbackVO> close(@PathVariable Long feedbackId) {
        return R.ok(adminFeedbackService.close(feedbackId));
    }

    /**
     * 重新打开反馈：已禁用。
     *
     * @param feedbackId 反馈 ID
     * @return 恒返回 403
     */
    @Operation(summary = "重新打开反馈（已禁用）", description = "状态流转由用户追问驱动，运营不可重开")
    @PostMapping("/{feedbackId}/reopen")
    public R<AdminFeedbackVO> reopen(@PathVariable Long feedbackId) {
        return R.ok(adminFeedbackService.reopen(feedbackId));
    }

    /**
     * 管理端未读数（管理入口数字角标）。
     *
     * @return 未读用户消息数
     */
    @Operation(summary = "反馈未读数", description = "未读的新反馈首条 + 用户新评论")
    @GetMapping("/unread")
    public R<Long> unread() {
        return R.ok(adminFeedbackService.unreadCount());
    }
}
