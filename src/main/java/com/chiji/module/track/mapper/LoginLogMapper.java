package com.chiji.module.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志表 Mapper。
 * <p>
 * 仅插入与保留期物理清理，无更新语义。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 区间内去重登录用户数（运营日汇总用）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return distinct user_id 数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM login_log WHERE login_at >= #{start} AND login_at < #{end}")
    int countDistinctUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按日去重登录用户数（管理端看板趋势）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日 distinct 用户数
     */
    @Select("SELECT DATE(login_at) AS date, COUNT(DISTINCT user_id) AS value "
            + "FROM login_log WHERE login_at >= #{start} AND login_at < #{end} "
            + "GROUP BY DATE(login_at)")
    List<DayValueRow> countDistinctUsersByDay(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
}
