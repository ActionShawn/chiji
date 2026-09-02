package com.chiji.module.auth.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.dto.UpdateUserProfileRequest;
import com.chiji.module.auth.service.UserService;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.auth.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
