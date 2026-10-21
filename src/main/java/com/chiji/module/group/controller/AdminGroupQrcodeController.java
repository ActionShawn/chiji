package com.chiji.module.group.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.group.dto.GroupQrcodeUpdateRequest;
import com.chiji.module.group.service.GroupQrcodeService;
import com.chiji.module.group.vo.GroupQrcodeVO;
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
 * 管理端交流群二维码维护接口。
 * <p>
 * 路由位于 {@code /api/admin/**}，由 SaTokenConfig 在登录校验之上统一强制
 * 管理员角色校验（非 ADMIN 返回 403），控制器内不重复实现角色判断。
 */
@Tag(name = "管理端-交流群二维码", description = "二维码上传/更换（仅管理员）")
@RestController
@RequestMapping("/api/admin/group-qrcode")
@RequiredArgsConstructor
public class AdminGroupQrcodeController {

    private final GroupQrcodeService groupQrcodeService;

    /**
     * 读取当前二维码（管理端预览用）。
     *
     * @return {imageUrl, updatedAt}；未配置返回空对象
     */
    @Operation(summary = "读取交流群二维码", description = "管理端预览：当前二维码地址与最近更新时间")
    @GetMapping
    public R<GroupQrcodeVO> get() {
        return R.ok(groupQrcodeService.getQrcode());
    }

    /**
     * 更新二维码地址（管理员上传/更换）。
     * <p>
     * 图片字节不经本接口：管理端先走 {@code POST /api/files/upload-url} COS 直传拿 url，
     * 再将 url 传入本接口落库；更新后用户端下次进入交流群页即展示新图。
     *
     * @param request 二维码地址（https，长度 ≤ 512）
     * @return 保存后的整读（管理端即时刷新预览与更新时间）
     */
    @Operation(summary = "更换交流群二维码", description = "传入 COS 直传返回的 https url；更新后用户端实时生效")
    @PutMapping
    public R<GroupQrcodeVO> update(@Valid @RequestBody GroupQrcodeUpdateRequest request) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        return R.ok(groupQrcodeService.updateQrcode(request, adminUserId));
    }
}
