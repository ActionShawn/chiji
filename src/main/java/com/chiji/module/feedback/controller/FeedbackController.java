package com.chiji.module.feedback.controller;

import com.chiji.common.core.page.CursorPage;
import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.service.FeedbackService;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackStatusVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;
import com.chiji.module.feedback.vo.FeedbackVO;
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
 * 意见反馈接口（用户端）。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头，userId 从上下文获取。
 * 提供提交、历史、对话线程、追加评论与闭环/撤回能力。
 */
@Tag(name = "反馈", description = "意见反馈的提交、历史与连续对话")
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交意见（正文 + 图片 + 可选联系方式）。
     *
     * @param request 提交请求
     * @return 保存后的反馈 VO
     */
    @Operation(summary = "提交意见", description = "正文 + 图片（最多3张）+ 可选联系方式（手机号/邮箱）")
    @PostMapping
    public R<FeedbackVO> submit(@Valid @RequestBody SubmitFeedbackRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.submit(userId, request), "已收到，感谢反馈");
    }

    /**
     * 分页查询当前用户的反馈历史（游标分页，按 id 倒序）。
     *
     * @param limit  每页条数（默认 20，最大 50）
     * @param cursor 上一页最后一条 id（首页不传）
     * @return 游标分页结果
     */
    @Operation(summary = "查询反馈历史", description = "游标分页，按提交时间倒序")
    @GetMapping
    public R<CursorPage<FeedbackVO>> list(@RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(required = false) Long cursor) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.listByUser(userId, limit, cursor));
    }

    /**
     * 拉取对话线程（反馈本体 + 最新一页评论），进入即清该反馈的用户侧未读。
     *
     * @param feedbackId 反馈 ID
     * @return 对话线程
     */
    @Operation(summary = "拉取对话线程", description = "首条=反馈本体；评论取最新一页升序；进入即清未读")
    @GetMapping("/{feedbackId}/thread")
    public R<FeedbackThreadVO<FeedbackVO>> thread(@PathVariable Long feedbackId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.thread(userId, feedbackId));
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
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.comments(userId, feedbackId, limit, cursor));
    }

    /**
     * 用户追加评论（正文/图片至少一项，允许仅图片）。
     *
     * @param feedbackId 反馈 ID
     * @param request    评论请求
     * @return 新消息 + 评论后状态
     */
    @Operation(summary = "追加评论", description = "允许仅图片；CLOSED 下追加即追问自动复活为等待回复")
    @PostMapping("/{feedbackId}/comments")
    public R<AddCommentResultVO> addComment(@PathVariable Long feedbackId,
                                            @Valid @RequestBody AddCommentRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.addComment(userId, feedbackId, request));
    }

    /**
     * 点「已解决」闭环（5 秒内可撤回）。
     *
     * @param feedbackId 反馈 ID
     * @return 闭环后状态（含撤回窗口剩余秒数）
     */
    @Operation(summary = "闭环反馈", description = "用户点已解决；幂等，重复调用不刷新撤回窗口")
    @PostMapping("/{feedbackId}/resolve")
    public R<FeedbackStatusVO> resolve(@PathVariable Long feedbackId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.resolve(userId, feedbackId));
    }

    /**
     * 撤回闭环（仅闭环后 5 秒内有效）。
     *
     * @param feedbackId 反馈 ID
     * @return 撤回后状态
     */
    @Operation(summary = "撤回闭环", description = "仅闭环后5秒内可撤回，超时返回400")
    @PostMapping("/{feedbackId}/resolve/revert")
    public R<FeedbackStatusVO> revertResolve(@PathVariable Long feedbackId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.revertResolve(userId, feedbackId));
    }

    /**
     * 用户侧未读数（入口红点，>0 即亮）。
     *
     * @return 未读运营评论数
     */
    @Operation(summary = "反馈未读数", description = "运营在用户已读之后的新评论数")
    @GetMapping("/unread")
    public R<Long> unread() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(feedbackService.unreadCount(userId));
    }
}
