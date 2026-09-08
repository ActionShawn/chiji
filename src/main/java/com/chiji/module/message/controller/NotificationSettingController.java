package com.chiji.module.message.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.message.dto.NotificationUpdateRequest;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.vo.NotificationSettingsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知设置接口（佩戴打卡 + 矫正进度两组提醒的总开关/弹窗/红点/预设/勿扰/逐类开关）。
 * <p>
 * 受 Sa-Token 保护；写入为局部更新语义（未提供字段保持不变）。
 */
@Tag(name = "通知设置", description = "佩戴/矫正提醒偏好设置")
@RestController
@RequestMapping("/api/users/me/settings/notification")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Operation(summary = "整读通知设置", description = "含单值偏好、勿扰时段与 8 类开关明细")
    @GetMapping
    public R<NotificationSettingsVO> get() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(notificationSettingService.getSettings(userId));
    }

    @Operation(summary = "局部更新通知设置", description = "preset 一键应用或 types 逐项覆盖；返回更新后的整读")
    @PutMapping
    public R<NotificationSettingsVO> update(@RequestBody NotificationUpdateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(notificationSettingService.updateSettings(userId, req));
    }
}
