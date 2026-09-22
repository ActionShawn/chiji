package com.chiji.module.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.ClinicExpense;
import org.apache.ibatis.annotations.Mapper;

/**
 * 矫正花费流水 Mapper。
 * <p>
 * 统计聚合（分类小计/月度趋势）由 Service 拉取流水后在内存聚合（个人数据量小，
 * 避免维护 XML 聚合 SQL），user_id + expense_date 索引覆盖查询。
 */
@Mapper
public interface ClinicExpenseMapper extends BaseMapper<ClinicExpense> {
}
