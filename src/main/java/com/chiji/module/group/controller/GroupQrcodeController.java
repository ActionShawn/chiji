package com.chiji.module.group.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.group.service.GroupQrcodeService;
import com.chiji.module.group.vo.GroupQrcodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户交流群二维码接口（用户端）。
 * <p>
 * 受 Sa-Token 登录校验保护（userId 从上下文获取，本接口不区分角色）；
 * 管理端维护接口见 {@link AdminGroupQrcodeController}（/api/admin/** 角色校验兜底）。
 */
@Tag(name = "交流群二维码", description = "用户端二维码读取（管理员维护见管理端接口）")
@RestController
@RequestMapping("/api/group-qrcode")
@RequiredArgsConstructor
public class GroupQrcodeController {

    private final GroupQrcodeService groupQrcodeService;

    /**
     * 读取当前二维码（登录用户可访问）。
     *
     * @return {imageUrl, updatedAt}；未配置返回空对象（imageUrl=null），客户端渲染空态而非报错
     */
    @Operation(summary = "读取交流群二维码", description = "登录用户可访问；未配置返回空对象（imageUrl=null），不报错")
    @GetMapping
    public R<GroupQrcodeVO> get() {
        return R.ok(groupQrcodeService.getQrcode());
    }
}
