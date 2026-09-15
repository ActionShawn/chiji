package com.chiji.module.admin.service;

import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;

/**
 * 管理端运营看板服务。
 * <p>
 * 指标与趋势均实时聚合明细表（今日数据天然鲜活）；stat_daily 仅作长周期固化，
 * 不参与本期查询（口径与 stat_daily 同源，可抽核对一致）。
 */
public interface AdminStatsService {

    /**
     * 指标总览。
     *
     * @param period 周期：day（今日）/ week（近 7 天）/ month（近 30 天），非法值按 day
     * @return 区间聚合指标
     */
    AdminStatsOverviewVO overview(String period);

    /**
     * 近 7 天趋势（含今日）。
     *
     * @return 7 天数据点序列（缺数据日补 0）
     */
    AdminStatsTrendVO trend();
}
