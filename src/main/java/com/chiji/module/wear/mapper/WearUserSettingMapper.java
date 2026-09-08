package com.chiji.module.wear.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户设置表 Mapper（佩戴模块只读写 {@code goal_sec} 列）。
 * <p>
 * 通知偏好列由 message 模块的 {@code UserSettingMapper} 负责，两模块按各自列分区写同一行
 * （互不重叠），首次写前各自懒创建行并幂等处理唯一键冲突。
 */
@Mapper
public interface WearUserSettingMapper extends BaseMapper<UserSetting> {
}
