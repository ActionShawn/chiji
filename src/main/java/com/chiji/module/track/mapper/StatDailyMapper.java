package com.chiji.module.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.StatDaily;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运营日汇总表 Mapper。
 * <p>
 * 主键为 stat_date（手动赋值），写入前先按日期物理删除保证幂等（先删后插）。
 */
@Mapper
public interface StatDailyMapper extends BaseMapper<StatDaily> {
}
