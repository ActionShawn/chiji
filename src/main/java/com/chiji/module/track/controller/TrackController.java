package com.chiji.module.track.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chiji.common.core.result.R;
import com.chiji.module.track.dto.TrackSessionRequest;
import com.chiji.module.track.service.UsageSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点上报接口（App 级使用会话）。
 * <p>
 * 普通登录态（非 admin），受 Sa-Token 保护；接口被监控拦截器排除（不自污染）。
 * 无论校验/限频结果如何均返回 ok——埋点失败对用户零感知。
 */
@Tag(name = "埋点-使用会话", description = "App 级使用会话上报（onHide 触发）")
@RestController
@RequestMapping("/api/track")
@RequiredArgsConstructor
public class TrackController {

    private final UsageSessionService usageSessionService;

    /**
     * 上报一段使用会话（App onHide 触发，含 storage 补报重发）。
     *
     * @param request 会话上报请求
     * @return 固定 ok
     */
    @Operation(summary = "使用会话上报", description = "幂等（clientSessionId 防重），超限静默丢弃")
    @PostMapping("/session")
    public R<Void> session(@Valid @RequestBody TrackSessionRequest request) {
        usageSessionService.record(StpUtil.getLoginIdAsLong(), request);
        return R.ok();
    }
}
