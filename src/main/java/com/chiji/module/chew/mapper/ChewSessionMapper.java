package com.chiji.module.chew.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.ChewSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 咬胶会话表 Mapper。
 * <p>
 * 写入走 {@link #insertIgnore}：{@code uk_chew_client_session} 唯一键冲突（重复开始/补报）时静默忽略，
 * 返回受影响行数 0，上层无需区分首次与重复。
 * <p>
 * 聚合/明细查询一律「已结束」口径（{@code ended_at IS NOT NULL AND deleted = 0}）；
 * 自定义 {@code @Select} 不受 {@code @TableLogic} 自动追加，故均显式声明 {@code deleted = 0}（硬性要求）。
 */
@Mapper
public interface ChewSessionMapper extends BaseMapper<ChewSession> {

    /**
     * 幂等插入：UNIQUE KEY uk_chew_client_session 冲突时忽略。
     * <p>
     * 注意：自定义 {@code @Insert} 不触发 MetaObjectHandler 自动填充，雪花 id / 审计字段由 service 显式赋值。
     *
     * @param s 会话实体（含雪花 id 与审计字段）
     * @return 受影响行数（0 = 重复被忽略）
     */
    @Insert("INSERT IGNORE INTO chew_session (id, user_id, stage_id, aligner_id, started_at, ended_at, "
            + "pause_sec, paused_at, target_sec, source, edited, client_session_id, created_at, updated_at, deleted) "
            + "VALUES (#{s.id}, #{s.userId}, #{s.stageId}, #{s.alignerId}, #{s.startedAt}, #{s.endedAt}, "
            + "#{s.pauseSec}, #{s.pausedAt}, #{s.targetSec}, #{s.source}, #{s.edited}, #{s.clientSessionId}, "
            + "#{s.createdAt}, #{s.updatedAt}, #{s.deleted})")
    int insertIgnore(@Param("s") ChewSession s);

    /**
     * 每日净时长（秒）：净时长 = (ended_at - started_at) - pause_sec，仅统计已结束段。
     *
     * @param userId 用户 ID
     * @param start  起始时刻（含）
     * @param end    结束时刻（不含）
     * @return 每日净时长（无记录返回空列表）
     */
    @Select("SELECT DATE(started_at) AS date, COALESCE(SUM(TIMESTAMPDIFF(SECOND, started_at, ended_at) - pause_sec), 0) AS value "
            + "FROM chew_session WHERE user_id = #{userId} AND deleted = 0 "
            + "AND ended_at IS NOT NULL AND started_at >= #{start} AND started_at < #{end} "
            + "GROUP BY DATE(started_at) ORDER BY date")
    List<DayValueRow> sumNetSecByDay(@Param("userId") Long userId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 每日次数（仅已结束段）。
     *
     * @param userId 用户 ID
     * @param start  起始时刻（含）
     * @param end    结束时刻（不含）
     * @return 每日次数（无记录返回空列表）
     */
    @Select("SELECT DATE(started_at) AS date, COUNT(*) AS value "
            + "FROM chew_session WHERE user_id = #{userId} AND deleted = 0 "
            + "AND ended_at IS NOT NULL AND started_at >= #{start} AND started_at < #{end} "
            + "GROUP BY DATE(started_at) ORDER BY date")
    List<DayValueRow> countByDay(@Param("userId") Long userId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 某自然日逐条明细（含未结束，供日历页当日明细；聚合口径由上层按需过滤）。
     *
     * @param userId 用户 ID
     * @param start  起始时刻（含）
     * @param end    结束时刻（不含）
     * @return 当日会话列表（按 started_at 升序）
     */
    @Select("SELECT * FROM chew_session WHERE user_id = #{userId} AND deleted = 0 "
            + "AND started_at >= #{start} AND started_at < #{end} ORDER BY started_at")
    List<ChewSession> listOfDay(@Param("userId") Long userId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);
}
