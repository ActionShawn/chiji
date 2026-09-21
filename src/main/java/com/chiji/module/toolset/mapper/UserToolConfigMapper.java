package com.chiji.module.toolset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserToolConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 首页常用工具配置 Mapper（读写 {@code user_tool_config} 表中用户自选工具 key 有序列表）。
 */
@Mapper
public interface UserToolConfigMapper extends BaseMapper<UserToolConfig> {
}
