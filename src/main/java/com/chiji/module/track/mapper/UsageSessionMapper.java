package com.chiji.module.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.common.core.dto.LabelValueRow;
import com.chiji.entity.UsageSession;
import com.chiji.module.track.dto.UsageRankRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 使用会话表 Mapper。
 * <p>
 * 写入走 {@link #insertIgnore}：client_session_id 唯一键冲突（补报重复）时静默忽略，
 * 返回受影响行数 0，上层无需区分首次与重复。
 */
@Mapper
public interface UsageSessionMapper extends BaseMapper<UsageSession> {

    /**
     * 幂等插入：UNIQUE KEY uk_client_session 冲突时忽略。
     *
     * @param session 会话实体（含雪花 id，由实体生成）
     * @return 受影响行数（0=重复被忽略）
     */
    @Insert("INSERT IGNORE INTO usage_session (id, user_id, client_session_id, enter_at, duration_sec, created_at) "
            + "VALUES (#{session.id}, #{session.userId}, #{session.clientSessionId}, #{session.enterAt}, "
            + "#{session.durationSec}, #{session.createdAt})")
    int insertIgnore(@Param("session") UsageSession session);

    /**
     * 区间内使用时长总和（秒，按 enter_at 归属）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 秒数总和（无记录返回 0）
     */
    @Select("SELECT COALESCE(SUM(duration_sec), 0) FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end}")
    long sumDurationSec(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 区间内去重使用用户数（按 enter_at 归属）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return distinct user_id 数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end}")
    int countDistinctUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按日使用时长总和（秒，管理端看板趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日秒数总和
     */
    @Select("SELECT DATE(enter_at) AS date, COALESCE(SUM(duration_sec), 0) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY DATE(enter_at)")
    List<DayValueRow> sumDurationSecByDay(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * 按日去重使用用户数（管理端看板趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日 distinct 用户数
     */
    @Select("SELECT DATE(enter_at) AS date, COUNT(DISTINCT user_id) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY DATE(enter_at)")
    List<DayValueRow> countDistinctUsersByDay(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 按小时使用时长总和（秒，管理端看板当日趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每小时秒数（label=两位小时「09」）
     */
    @Select("SELECT DATE_FORMAT(enter_at, '%H') AS label, COALESCE(SUM(duration_sec), 0) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY label")
    List<LabelValueRow> sumDurationSecByHour(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * 按月使用时长总和（秒，管理端看板全部趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每月秒数（label=「2026-09」）
     */
    @Select("SELECT DATE_FORMAT(enter_at, '%Y-%m') AS label, COALESCE(SUM(duration_sec), 0) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY label")
    List<LabelValueRow> sumDurationSecByMonth(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 按小时使用用户数去重（管理端看板当日趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每小时去重人数（label=两位小时「09」）
     */
    @Select("SELECT DATE_FORMAT(enter_at, '%H') AS label, COUNT(DISTINCT user_id) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY label")
    List<LabelValueRow> countDistinctUsersByHour(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    /**
     * 按月使用用户数去重（管理端看板全部趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每月去重人数（label=「2026-09」）
     */
    @Select("SELECT DATE_FORMAT(enter_at, '%Y-%m') AS label, COUNT(DISTINCT user_id) AS value "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY label")
    List<LabelValueRow> countDistinctUsersByMonth(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /**
     * 区间内用户使用时长排行（管理端运营看板，时长倒序取前 N）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @param limit 取前 N 名
     * @return 排行行（userId + 时长秒数）
     */
    @Select("SELECT user_id AS userId, COALESCE(SUM(duration_sec), 0) AS totalSec "
            + "FROM usage_session WHERE enter_at >= #{start} AND enter_at < #{end} "
            + "GROUP BY user_id ORDER BY totalSec DESC LIMIT #{limit}")
    List<UsageRankRow> rankByDuration(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("limit") int limit);
}
