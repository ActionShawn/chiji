package com.chiji.module.admin.controller;

import com.chiji.common.core.page.CursorPage;
import com.chiji.common.core.result.R;
import com.chiji.module.admin.service.AdminMetricsService;
import com.chiji.module.admin.vo.ApiHourlyVO;
import com.chiji.module.admin.vo.ApiStatsVO;
import com.chiji.module.admin.vo.ApiTrendVO;
import com.chiji.module.admin.vo.SlowApiLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端接口监控接口。
 * <p>
 * 受 Sa-Token 登录校验 + 管理员角色校验双重保护（见 SaTokenConfig）。
 */
@Tag(name = "管理端-接口监控", description = "接口调用量/错误率/慢请求与小时分布")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    /**
     * 接口监控聚合列表（调用量倒序）。
     *
     * @param range 区间：all（全部历史）；可空
     * @param days  统计天数（1-30，含今日，默认 7；range=all 时忽略）
     * @return 接口聚合行
     */
    @Operation(summary = "接口列表", description = "区间内各接口调用量/失败数/失败率/慢请求数/最大耗时")
    @GetMapping("/api-list")
    public R<List<ApiStatsVO>> apiList(@RequestParam(required = false) String range,
                                       @RequestParam(defaultValue = "7") int days) {
        return R.ok(adminMetricsService.apiList(range, days));
    }

    /**
     * 接口趋势序列（运维看板折线：调用次数 / 最大耗时）。
     *
     * @param range 区间：day / week / month / all，默认 day
     * @return 数据点序列（缺数据分组补 0）
     */
    @Operation(summary = "接口趋势", description = "调用次数/失败数/最大耗时序列，粒度自适应小时/日/月")
    @GetMapping("/trend")
    public R<ApiTrendVO> trend(@RequestParam(defaultValue = "day") String range) {
        return R.ok(adminMetricsService.trend(range));
    }

    /**
     * 单接口某日 24 小时分布。
     *
     * @param api      接口模板
     * @param statDate 统计日期（yyyy-MM-dd，默认今日）
     * @return 24 小时分布
     */
    @Operation(summary = "小时视图", description = "单接口某日 0-23 时调用量/失败/慢请求分布")
    @GetMapping("/hourly")
    public R<ApiHourlyVO> hourly(@RequestParam String api,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate) {
        return R.ok(adminMetricsService.hourly(api, statDate));
    }

    /**
     * 慢请求/错误明细（游标分页）。
     *
     * @param api      接口模板筛选（可空）
     * @param statDate 日期筛选（可空）
     * @param limit    每页条数（默认 20，最大 50）
     * @param cursor   上一页最后一条 id（首页不传）
     * @return 游标分页结果
     */
    @Operation(summary = "慢请求明细", description = "slow_api_log 游标分页，可按接口/日期筛选")
    @GetMapping("/slow")
    public R<CursorPage<SlowApiLogVO>> slowList(@RequestParam(required = false) String api,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
                                                @RequestParam(defaultValue = "20") int limit,
                                                @RequestParam(required = false) Long cursor) {
        return R.ok(adminMetricsService.slowList(api, statDate, limit, cursor));
    }
}
