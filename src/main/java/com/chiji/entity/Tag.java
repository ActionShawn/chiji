// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\Tag.java
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
 * 标签实体。
 * <p>
 * 记录弹层的标签来源：系统内置预设（换牙套/复诊/疼痛/适应中/小进步/里程碑/日常，
 * {@code userId = 0}）与用户自定义标签。约束：同一 {@code userId} 下 {@code name} 唯一
 * （UNIQUE INDEX：uk_tag_user_name）。关系：N 标签 → N 记录（经 {@link RecordTagRelation}）。
 */
@Getter
@Setter
@ToString
@TableName("tag")
public class Tag {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID；0 表示系统内置预设标签（联合唯一索引 uk_tag_user_name 前缀） */
    private Long userId;

    /** 标签名（同一用户下唯一，VARCHAR(32)） */
    private String name;

    /** 标签颜色，形如 #FF6B6B（VARCHAR(7)，可为空） */
    private String colorHex;

    /** 排序权重，越小越靠前（INT，默认 0） */
    private Integer sortOrder;

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
