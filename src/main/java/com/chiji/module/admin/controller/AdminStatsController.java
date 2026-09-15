package com.chiji.module.admin.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.admin.service.AdminStatsService;
import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端运营看板接口。
 * <p>
 * 受 Sa-Token 登录校验 + 管理员角色校验双重保护（见 SaTokenConfig）。
 */
@Tag(name = "管理端-运营看板", description = "用户/登录/使用/接口/内容指标与趋势")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /**
     * 指标总览（今日 / 近 7 天 / 近 30 天）。
     *
     * @param period 周期：day / week / month，默认 day
     * @return 区间聚合指标
     */
    @Operation(summary = "指标总览", description = "实时聚合：新增用户/登录用户/使用时长/接口调用/新增记录/新增图片")
    @GetMapping("/overview")
    public R<AdminStatsOverviewVO> overview(@RequestParam(defaultValue = "day") String period) {
        return R.ok(adminStatsService.overview(period));
    }

    /**
     * 近 7 天趋势（含今日，一次返回全部指标序列）。
     *
     * @return 7 天数据点
     */
    @Operation(summary = "近7天趋势", description = "全部指标序列，前端本地切换趋势线")
    @GetMapping("/trend")
    public R<AdminStatsTrendVO> trend() {
        return R.ok(adminStatsService.trend());
    }
}
