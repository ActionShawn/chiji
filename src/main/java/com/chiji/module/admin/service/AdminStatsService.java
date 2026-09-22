package com.chiji.module.admin.service;

import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;
import com.chiji.module.admin.vo.UsageRankVO;

import java.util.List;

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
     * @param period 周期：day（今日）/ week（近 7 天）/ month（近 30 天）/ all（全部），非法值按 day
     * @return 区间聚合指标
     */
    AdminStatsOverviewVO overview(String period);

    /**
     * 趋势序列（粒度自适应：当天=小时、近 7/30 天=日、全部=月）。
     *
     * @param range 区间：day / week / month / all，非法值按 day
     * @return 数据点序列（缺数据分组补 0）
     */
    AdminStatsTrendVO trend(String range);

    /**
     * 使用时长排行（前 N 名，昵称脱敏）。
     *
     * @param period 周期：day / week / month / all，非法值按 day
     * @return 排行列表（时长倒序）
     */
    List<UsageRankVO> usageRank(String period);
}
