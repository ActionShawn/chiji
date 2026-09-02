package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.RecordMedia;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记录媒体表 Mapper。
 * <p>
 * 保存记录时批量插入媒体；查询时按 record_id 加载媒体列表。
 */
@Mapper
public interface RecordMediaMapper extends BaseMapper<RecordMedia> {
}
