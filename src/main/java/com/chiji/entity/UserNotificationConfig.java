package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户通知类型开关实体。
 * <p>
 * 每行 = 某用户某提醒类型（{@link com.chiji.enums.WearReminderTypeEnum}）的开关。配合
 * {@code user_setting} 上的单值偏好（总开关/弹窗/红点/预设/勿扰）一起构成通知设置。
 * 未落行（用户从未拨动过）的类型在读取时按类型预设默认（{@code isDefaultOn}）呈现，仅在
 * 首次手工修改或应用预设时写入。
 */
@Getter
@Setter
@ToString
@TableName("user_notification_config")
public class UserNotificationConfig {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（唯一索引 uk_notif_cfg_user_type 前缀） */
    private Long userId;

    /** 提醒类型：WearReminderTypeEnum.name() */
    private String reminderType;

    /** 是否开启 */
    private Boolean enabled;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
