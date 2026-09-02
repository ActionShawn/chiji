package com.chiji.module.stage.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.stage.service.HomeService;
import com.chiji.module.stage.vo.HomeSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 */
@Tag(name = "首页", description = "首页聚合数据")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 获取首页聚合数据（按记录模式过滤）。
     * <p>
     * 一次返回该模式下的阶段列表、当前阶段的牙套节点、鼓励横幅，供小程序首页渲染。
     * 记录模式即工作台：单模 / 双模的阶段数据彼此独立，模式由前端切换传入。
     *
     * @param mode 记录模式编码（CLEAR_SINGLE / CLEAR_DUAL，缺省 CLEAR_SINGLE）
     * @return 首页聚合数据
     */
    @Operation(summary = "首页聚合数据", description = "按记录模式过滤，一次返回阶段、牙套节点、横幅")
    @GetMapping("/summary")
    public R<HomeSummaryVO> getHomeSummary(@RequestParam(required = false) String mode) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(homeService.getHomeSummary(userId, mode));
    }
}
