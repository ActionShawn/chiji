package com.chiji.module.admin.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.admin.service.AdminStatsService;
import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;
import com.chiji.module.admin.vo.UsageRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * 指标总览（今日 / 近 7 天 / 近 30 天 / 全部）。
     *
     * @param period 周期：day / week / month / all，默认 day
     * @return 区间聚合指标
     */
    @Operation(summary = "指标总览", description = "实时聚合：新增用户/登录用户/使用时长/接口调用/新增记录/新增图片")
    @GetMapping("/overview")
    public R<AdminStatsOverviewVO> overview(@RequestParam(defaultValue = "day") String period) {
        return R.ok(adminStatsService.overview(period));
    }

    /**
     * 趋势序列（粒度自适应：当天=小时、近 7/30 天=日、全部=月）。
     *
     * @param range 区间：day / week / month / all，默认 day
     * @return 全部指标数据点（前端本地切换趋势线）
     */
    @Operation(summary = "趋势序列", description = "新增用户/使用时长/接口调用/新增记录/新增照片序列，缺数据分组补 0")
    @GetMapping("/trend")
    public R<AdminStatsTrendVO> trend(@RequestParam(defaultValue = "day") String range) {
        return R.ok(adminStatsService.trend(range));
    }

    /**
     * 使用时长排行（前 10 名，昵称脱敏）。
     *
     * @param period 周期：day / week / month / all，默认 day
     * @return 排行列表（时长倒序）
     */
    @Operation(summary = "使用时长排行", description = "按用户聚合时长取前 10，昵称脱敏展示保护隐私")
    @GetMapping("/usage-rank")
    public R<List<UsageRankVO>> usageRank(@RequestParam(defaultValue = "day") String period) {
        return R.ok(adminStatsService.usageRank(period));
    }
}
