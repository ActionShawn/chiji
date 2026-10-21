package com.chiji.module.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.GroupQrcodeConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户交流群二维码配置 Mapper（读写 {@code group_qrcode_config} 单行配置表）。
 * <p>
 * 由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 */
@Mapper
public interface GroupQrcodeConfigMapper extends BaseMapper<GroupQrcodeConfig> {
}
