package com.chiji.module.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.ClinicVisit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复诊档案 Mapper。
 * <p>
 * 常规读写走 BaseMapper 通用方法；按月取数由 Service 用 LambdaQueryWrapper 完成
 * （user_id + visit_date 索引覆盖）。
 */
@Mapper
public interface ClinicVisitMapper extends BaseMapper<ClinicVisit> {
}
