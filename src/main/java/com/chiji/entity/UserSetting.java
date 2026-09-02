// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\UserSetting.java
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
import java.time.LocalTime;

/**
 * 用户设置实体（一对一扩展 User）。
 * <p>
 * 存储主题模式、字号、云同步开关与每日提醒时间。约束：{@code userId} 唯一
 * （UNIQUE INDEX：uk_user_setting_user）。关系：1 设置 → 1 用户。
 */
@Getter
@Setter
@ToString
@TableName("user_setting")
public class UserSetting {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID（一对一，全局唯一 UNIQUE INDEX：uk_user_setting_user） */
    private Long userId;

    /** 主题模式，存 {@link com.chiji.enums.ThemeModeEnum} 的 name()：LIGHT/DARK/AUTO */
    private String themeMode;

    /** 字体大小，存 {@link com.chiji.enums.FontSizeEnum} 的 name()：SMALL/MEDIUM/LARGE */
    private String fontSize;

    /** 是否启用云同步（默认 false） */
    private Boolean enableCloudSync;

    /** 每日提醒时间（可选，未设置为 null） */
    private LocalTime reminderTime;

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
