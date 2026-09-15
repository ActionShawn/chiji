package com.chiji.module.admin.service;

import com.chiji.common.core.page.CursorPage;
import com.chiji.module.admin.vo.ApiHourlyVO;
import com.chiji.module.admin.vo.ApiStatsVO;
import com.chiji.module.admin.vo.SlowApiLogVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端接口监控服务。
 * <p>
 * 数据源为 api_metric_hourly 聚合表（≤5 分钟刷盘延迟）与 slow_api_log 明细表。
 */
public interface AdminMetricsService {

    /**
     * 接口监控聚合列表（调用量倒序）。
     *
     * @param days 统计天数（1-30，含今日，默认 7）
     * @return 接口聚合行
     */
    List<ApiStatsVO> apiList(int days);

    /**
     * 单接口某日 24 小时分布。
     *
     * @param api      接口模板（必填）
     * @param statDate 统计日期（默认今日）
     * @return 24 小时分布（无数据小时补 0）
     */
    ApiHourlyVO hourly(String api, LocalDate statDate);

    /**
     * 慢请求/错误明细（游标分页，按 id 倒序）。
     *
     * @param api      接口模板筛选（可空=全部）
     * @param statDate 日期筛选（可空=全部日期，含该日 00:00-24:00）
     * @param limit    每页条数（默认 20，最大 50）
     * @param cursor   上一页最后一条 id（首页不传）
     * @return 游标分页结果
     */
    CursorPage<SlowApiLogVO> slowList(String api, LocalDate statDate, int limit, Long cursor);
}
