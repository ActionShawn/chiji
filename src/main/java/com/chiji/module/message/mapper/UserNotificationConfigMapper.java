package com.chiji.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserNotificationConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知类型开关表 Mapper。
 */
@Mapper
public interface UserNotificationConfigMapper extends BaseMapper<UserNotificationConfig> {
}
