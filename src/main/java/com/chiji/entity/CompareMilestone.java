// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\CompareMilestone.java
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
 * 对比印记实体（「看见变化」页的命名前后对比）。
 * <p>
 * 对应 {@code saveMilestone}：选择 before/after 两张素材并命名保存。
 * 弹窗中的标签胶囊（效果对比 + 两素材所属阶段）与日期范围由
 * before/after 媒体及其所属记录派生，不落库。
 */
@Getter
@Setter
@ToString
@TableName("compare_milestone")
public class CompareMilestone {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（索引 idx_milestone_user） */
    private Long userId;

    /** 印记名称（保存对比印记弹窗命名，最多 30 字） */
    private String name;

    /** 对比前素材（record_media.id） */
    private Long beforeMediaId;

    /** 对比后素材（record_media.id） */
    private Long afterMediaId;

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
