package com.chiji.module.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.SlowApiLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 慢请求/错误明细表 Mapper。
 * <p>
 * 仅插入（阈值采集）与保留期物理清理。
 */
@Mapper
public interface SlowApiLogMapper extends BaseMapper<SlowApiLog> {
}
