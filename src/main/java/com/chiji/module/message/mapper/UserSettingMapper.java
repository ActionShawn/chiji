package com.chiji.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户设置表 Mapper（消息模块按列读写通知相关偏好：总开关/弹窗/红点/预设/勿扰）。
 * <p>
 * 单表归属说明：{@code wear} 模块另有 {@code WearUserSettingMapper} 读 {@code goal_sec}，
 * 两模块按各自列分区写同一行（互不重叠），首次写前各自懒创建行并幂等处理唯一键冲突。
 */
@Mapper
public interface UserSettingMapper extends BaseMapper<UserSetting> {
}
