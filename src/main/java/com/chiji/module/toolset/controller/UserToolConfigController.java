package com.chiji.module.toolset.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.toolset.dto.ToolKeysUpdateRequest;
import com.chiji.module.toolset.service.UserToolConfigService;
import com.chiji.module.toolset.vo.ToolConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页常用工具配置接口（用户自选工具与排序；工具字典由客户端承载）。
 * <p>
 * 受 Sa-Token 保护；写入为全量覆盖语义（传入列表顺序即展示顺序）。
 */
@Tag(name = "常用工具配置", description = "首页常用工具的个人选择与排序")
@RestController
@RequestMapping("/api/users/me/settings/tools")
@RequiredArgsConstructor
public class UserToolConfigController {

    private final UserToolConfigService userToolConfigService;

    @Operation(summary = "整读常用工具配置", description = "返回工具 key 有序列表；未自定义时为空列表（客户端回退默认）")
    @GetMapping
    public R<ToolConfigVO> get() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userToolConfigService.getToolConfig(userId));
    }

    @Operation(summary = "全量覆盖保存常用工具配置", description = "传入列表顺序即展示顺序；返回保存后的整读")
    @PutMapping
    public R<ToolConfigVO> update(@RequestBody ToolKeysUpdateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(userToolConfigService.updateToolKeys(userId, req));
    }
}
