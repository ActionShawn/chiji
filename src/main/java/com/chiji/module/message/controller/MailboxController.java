package com.chiji.module.message.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.message.service.MessageService;
import com.chiji.module.message.vo.MessagePageVO;
import com.chiji.module.message.vo.UnreadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信箱消息接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。分页游标为上一页最后一条 id，
 * 消息写入由佩戴时长/通知侧 {@code ReminderNotifyService} 统一生成，本控制器只做读/已读/删除。
 */
@Tag(name = "消息", description = "信箱消息（列表/未读/已读/删除）")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MailboxController {

    private final MessageService messageService;

    @Operation(summary = "信箱消息分页", description = "按 id 倒序返回，可选按分类过滤；同时返回筛选范围内未读数")
    @GetMapping
    public R<MessagePageVO> list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String category) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(messageService.listMessages(userId, cursor, pageSize, category));
    }

    @Operation(summary = "信箱未读数", description = "全部未读消息总数（供角标）")
    @GetMapping("/unread-count")
    public R<UnreadVO> unreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(new UnreadVO(messageService.countUnread(userId)));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        messageService.markRead(userId, id);
        return R.ok();
    }

    @Operation(summary = "全部标为已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        messageService.markAllRead(userId);
        return R.ok();
    }

    @Operation(summary = "删除单条消息", description = "逻辑删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        messageService.deleteMessage(userId, id);
        return R.ok();
    }
}
