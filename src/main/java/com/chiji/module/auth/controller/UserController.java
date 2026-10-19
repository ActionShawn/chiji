package com.chiji.module.auth.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.dto.UpdateUserProfileRequest;
import com.chiji.module.auth.dto.UserCancelRequest;
import com.chiji.module.auth.service.UserCancelService;
import com.chiji.module.auth.service.UserService;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.auth.vo.CancelPreviewVO;
import com.chiji.module.auth.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头，userId 从上下文获取。
 */
@Tag(name = "用户", description = "个人资料查询与更新")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserCancelService userCancelService;

    /**
     * 查询个人资料。
     *
     * @return 用户资料 VO
     */
    @Operation(summary = "查询个人资料", description = "昵称/头像/手机号/治疗方式")
    @GetMapping("/me")
    public R<UserProfileVO> getProfile() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userService.getProfile(userId));
    }

    /**
     * 更新个人资料（昵称 / 头像）。
     *
     * @param request 更新请求（字段可单独提交，空字符串表示清除）
     * @return 更新后的用户资料 VO
     */
    @Operation(summary = "更新个人资料", description = "更新昵称/头像，字段可单独提交；传空字符串表示清除")
    @PutMapping("/me")
    public R<UserProfileVO> updateProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userService.updateProfile(userId, request), "资料已保存");
    }

    /**
     * 注销挽留屏数据盘点（只读）。
     *
     * @return 盘点 VO（已记录天数 / 媒体数 / 对比印记数 / 陪伴天数）
     */
    @Operation(summary = "注销数据盘点", description = "挽留屏「你即将失去什么」的具象化数据（只读）")
    @GetMapping("/me/cancel-preview")
    public R<CancelPreviewVO> cancelPreview() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userCancelService.preview(userId));
    }

    /**
     * 执行账号注销（立即物理删除，不可逆）。
     * <p>
     * 服务端校验 {@code confirm === "注销"}；幂等（user 行已不存在同样返回成功）。
     *
     * @param request 注销请求
     * @return 注销成功
     */
    @Operation(summary = "注销账号", description = "确认词必须为「注销」；立即删除业务数据与文件，不可恢复")
    @PostMapping("/me/cancel")
    public R<Void> cancel(@RequestBody UserCancelRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        userCancelService.cancel(userId, request);
        return R.ok(null, "账号已注销");
    }
}
