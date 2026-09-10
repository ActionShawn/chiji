package com.chiji.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserNotificationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户通知类型开关表 Mapper。
 */
@Mapper
public interface UserNotificationConfigMapper extends BaseMapper<UserNotificationConfig> {

    /**
     * 按 (userId, reminderType) 写入开关值并复活可能存在的逻辑删除行（把 deleted 复位为 0）。
     * <p>
     * 唯一键 {@code uk_notif_cfg_user_type (user_id, reminder_type)} 不含 {@code deleted}，
     * 物理上每类型至多一行；逻辑删除后的行仍占位，直接 insert 会撞唯一键。该自定义 SQL 不经过
     * MyBatis-Plus 的逻辑删除过滤，直接定位这唯一一行并同时完成「更新值 + 复活」。
     *
     * @param userId  用户 ID
     * @param type    提醒类型 code
     * @param enabled 开关值：1 开 / 0 关
     * @return 影响行数：>0 命中并更新（含复活）；0 物理行不存在，调用方应改走 insert
     */
    @Update("UPDATE user_notification_config "
            + "SET enabled = #{enabled}, deleted = 0, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND reminder_type = #{type}")
    int reactivateByUserType(@Param("userId") Long userId, @Param("type") String type, @Param("enabled") Integer enabled);
}
