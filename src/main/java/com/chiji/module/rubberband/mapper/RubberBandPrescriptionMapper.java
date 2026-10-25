package com.chiji.module.rubberband.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.module.rubberband.entity.RubberBandPrescription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 橡皮筋医嘱表 Mapper。
 * <p>
 * 单用户单行（唯一键 {@code uk_rbp_user}），由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 * 保存走「查一行 → 有则更新 / 无则插入」的 upsert（个人应用量级，无需原生 ON DUPLICATE KEY）。
 */
@Mapper
public interface RubberBandPrescriptionMapper extends BaseMapper<RubberBandPrescription> {
}
