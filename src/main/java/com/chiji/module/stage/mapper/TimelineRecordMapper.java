package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.TimelineRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 时光轴记录表 Mapper。
 * <p>
 * 首期仅用于派生 {@code alignerNodes.thumbs}（查询某牙套副下的最近记录）；
 * 后续时光轴页联调时扩展使用。
 */
@Mapper
public interface TimelineRecordMapper extends BaseMapper<TimelineRecord> {
}
