package com.chiji.module.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.ApiMetricHourly;
import com.chiji.module.track.dto.ApiHourlyRow;
import com.chiji.module.track.dto.ApiStatsRow;
import com.chiji.module.track.dto.MetricTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 接口小时聚合表 Mapper。
 * <p>
 * 刷盘走 {@link #upsertAccumulate}：按 (stat_date, hour, api) 唯一键，
 * 新行插入、已有行累加（cnt/err_cnt/slow_cnt 相加，max_ms 取较大值）。
 * 计数器刷盘前已清零，同窗口不存在重跑，累加语义正确。
 */
@Mapper
public interface ApiMetricHourlyMapper extends BaseMapper<ApiMetricHourly> {

    /**
     * 累加 upsert：冲突时累加已有行。
     *
     * @param row 聚合行（含雪花 id，冲突时沿用已有行 id）
     * @return 受影响行数（1=插入或更新）
     */
    @Insert("INSERT INTO api_metric_hourly (id, stat_date, hour, api, cnt, err_cnt, slow_cnt, max_ms) "
            + "VALUES (#{row.id}, #{row.statDate}, #{row.hour}, #{row.api}, #{row.cnt}, #{row.errCnt}, "
            + "#{row.slowCnt}, #{row.maxMs}) "
            + "ON DUPLICATE KEY UPDATE "
            + "cnt = cnt + VALUES(cnt), "
            + "err_cnt = err_cnt + VALUES(err_cnt), "
            + "slow_cnt = slow_cnt + VALUES(slow_cnt), "
            + "max_ms = GREATEST(max_ms, VALUES(max_ms))")
    int upsertAccumulate(@Param("row") ApiMetricHourly row);

    /**
     * 指定日期接口调用总数（运营日汇总用）。
     *
     * @param statDate 统计日期
     * @return cnt 总和（无记录返回 0）
     */
    @Select("SELECT COALESCE(SUM(cnt), 0) FROM api_metric_hourly WHERE stat_date = #{statDate}")
    long sumCnt(@Param("statDate") LocalDate statDate);

    /**
     * 区间内接口调用总数（管理端看板指标，今日含已刷盘部分 ≤5 分钟延迟）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return cnt 总和（无记录返回 0）
     */
    @Select("SELECT COALESCE(SUM(cnt), 0) FROM api_metric_hourly WHERE stat_date >= #{start} AND stat_date <= #{end}")
    long sumCntRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 按日接口调用总数（管理端看板趋势）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 每日 cnt 总和
     */
    @Select("SELECT stat_date AS date, SUM(cnt) AS value FROM api_metric_hourly "
            + "WHERE stat_date >= #{start} AND stat_date <= #{end} GROUP BY stat_date")
    List<DayValueRow> sumCntByDay(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 按接口聚合区间监控数据（管理端监控列表，调用量倒序）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 接口聚合行
     */
    @Select("SELECT api, SUM(cnt) AS cnt, SUM(err_cnt) AS err_cnt, SUM(slow_cnt) AS slow_cnt, "
            + "MAX(max_ms) AS max_ms FROM api_metric_hourly "
            + "WHERE stat_date >= #{start} AND stat_date <= #{end} "
            + "GROUP BY api ORDER BY cnt DESC")
    List<ApiStatsRow> aggregateByApi(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 单接口某日 24 小时分布（管理端监控小时视图）。
     *
     * @param api      接口模板
     * @param statDate 统计日期
     * @return 存在数据的小时行（无数据小时由 service 补 0）
     */
    @Select("SELECT hour, SUM(cnt) AS cnt, SUM(err_cnt) AS err_cnt, SUM(slow_cnt) AS slow_cnt, "
            + "MAX(max_ms) AS max_ms FROM api_metric_hourly "
            + "WHERE api = #{api} AND stat_date = #{statDate} GROUP BY hour ORDER BY hour")
    List<ApiHourlyRow> hourlyByApi(@Param("api") String api, @Param("statDate") LocalDate statDate);

    /**
     * 全接口按小时聚合（管理端运维看板当日折线）。
     *
     * @param statDate 统计日期
     * @return 每小时行（label=两位小时「09」，无数据小时由 service 补 0）
     */
    @Select("SELECT LPAD(hour, 2, '0') AS label, SUM(cnt) AS cnt, SUM(err_cnt) AS err_cnt, "
            + "MAX(max_ms) AS max_ms FROM api_metric_hourly "
            + "WHERE stat_date = #{statDate} GROUP BY hour ORDER BY hour")
    List<MetricTrendRow> metricTrendByHour(@Param("statDate") LocalDate statDate);

    /**
     * 全接口按日聚合（管理端运维看板近 7/30 天折线）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 每日行（label=「2026-09-20」，无数据日由 service 补 0）
     */
    @Select("SELECT DATE_FORMAT(stat_date, '%Y-%m-%d') AS label, SUM(cnt) AS cnt, "
            + "SUM(err_cnt) AS err_cnt, MAX(max_ms) AS max_ms FROM api_metric_hourly "
            + "WHERE stat_date >= #{start} AND stat_date <= #{end} GROUP BY stat_date ORDER BY stat_date")
    List<MetricTrendRow> metricTrendByDay(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 全接口按月聚合（管理端运维看板全部折线）。
     *
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 每月行（label=「2026-09」，无数据月份由 service 补 0）
     */
    @Select("SELECT DATE_FORMAT(stat_date, '%Y-%m') AS label, SUM(cnt) AS cnt, "
            + "SUM(err_cnt) AS err_cnt, MAX(max_ms) AS max_ms FROM api_metric_hourly "
            + "WHERE stat_date >= #{start} AND stat_date <= #{end} "
            + "GROUP BY label ORDER BY label")
    List<MetricTrendRow> metricTrendByMonth(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
