package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.RecordMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记录媒体表 Mapper。
 * <p>
 * 保存记录时批量插入媒体；查询时按 record_id 加载媒体列表。
 */
@Mapper
public interface RecordMediaMapper extends BaseMapper<RecordMedia> {

    /**
     * 按日新增媒体数（管理端看板趋势，原生 SQL 需显式排除逻辑删除）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日计数行
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS value "
            + "FROM record_media WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
            + "GROUP BY DATE(created_at)")
    List<DayValueRow> countByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
