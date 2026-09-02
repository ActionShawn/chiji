package com.chiji.module.feedback.controller;

import com.chiji.common.core.page.CursorPage;
import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.service.FeedbackService;
import com.chiji.module.feedback.vo.FeedbackVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 意见反馈接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头，userId 从上下文获取。
 * 提供意见提交与「我的反馈」游标分页查询能力。
 */
@Tag(name = "反馈", description = "意见信箱的提交与历史查询")
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
}
