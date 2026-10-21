package com.chiji.module.adjustment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.module.adjustment.entity.AdjustmentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复诊调整记录表 Mapper。
 * <p>
 * 由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 * 列表走 idx_adj_user_date（user_id + adjust_date 倒序），
 * 时光轴删除联动反查走 idx_adj_tr（timeline_record_id）。
 */
@Mapper
public interface AdjustmentRecordMapper extends BaseMapper<AdjustmentRecord> {
}
