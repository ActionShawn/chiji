package com.chiji.module.admin.controller;

import com.chiji.common.core.page.CursorPage;
import com.chiji.common.core.result.R;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.service.AdminFeedbackService;
import com.chiji.module.admin.vo.AdminFeedbackVO;
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
 */
@Tag(name = "管理端-反馈", description = "意见反馈的管理员处理")
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    /**
     * 全量反馈分页查询（游标分页，按提交时间倒序）。
     *
     * @param status 状态筛选（PENDING/PROCESSED，不传=全部）
     * @param limit  每页条数（默认 20，最大 50）
     * @param cursor 上一页最后一条 id（首页不传）
     * @return 游标分页结果
     */
    @Operation(summary = "反馈列表", description = "全量反馈游标分页，可按状态筛选")
    @GetMapping("/list")
    public R<CursorPage<AdminFeedbackVO>> list(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @RequestParam(required = false) Long cursor) {
        return R.ok(adminFeedbackService.list(status, limit, cursor));
    }

    /**
     * 回复反馈：写入回复并流转为已处理；可重复修改回复。
     *
     * @param feedbackId 反馈 ID
     * @param request    回复请求（≤500 字）
     * @return 更新后的反馈 VO
     */
    @Operation(summary = "回复反馈", description = "写入回复内容，状态流转为已处理；重复回复为修改")
    @PostMapping("/{feedbackId}/reply")
    public R<AdminFeedbackVO> reply(@PathVariable Long feedbackId,
                                    @Valid @RequestBody AdminReplyFeedbackRequest request) {
        return R.ok(adminFeedbackService.reply(feedbackId, request), "已回复");
    }

    /**
     * 关闭反馈：标记为已处理（不改动回复）。
     *
     * @param feedbackId 反馈 ID
     * @return 更新后的反馈 VO
     */
    @Operation(summary = "关闭反馈", description = "标记为已处理（如无效/重复反馈）")
    @PostMapping("/{feedbackId}/close")
    public R<AdminFeedbackVO> close(@PathVariable Long feedbackId) {
        return R.ok(adminFeedbackService.close(feedbackId), "已处理");
    }

    /**
     * 重新打开反馈：状态回退为待处理（保留回复内容）。
     *
     * @param feedbackId 反馈 ID
     * @return 更新后的反馈 VO
     */
    @Operation(summary = "重新打开反馈", description = "状态回退为待处理，保留回复内容")
    @PostMapping("/{feedbackId}/reopen")
    public R<AdminFeedbackVO> reopen(@PathVariable Long feedbackId) {
        return R.ok(adminFeedbackService.reopen(feedbackId), "已重新打开");
    }
}
