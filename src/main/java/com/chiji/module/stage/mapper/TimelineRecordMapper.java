package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.TimelineRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时光轴记录表 Mapper。
 * <p>
 * 首期仅用于派生 {@code alignerNodes.thumbs}（查询某牙套副下的最近记录）；
 * 后续时光轴页联调时扩展使用。
 */
@Mapper
public interface TimelineRecordMapper extends BaseMapper<TimelineRecord> {

    /**
     * 按日新增记录数（管理端看板趋势，原生 SQL 需显式排除逻辑删除）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日计数行
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS value "
            + "FROM timeline_record WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
            + "GROUP BY DATE(created_at)")
    List<DayValueRow> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
