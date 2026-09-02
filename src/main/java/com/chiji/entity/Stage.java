// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\Stage.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 矫正阶段实体。
 * <p>
 * 对应首页「阶段」卡片与开启新阶段表单（命名 + 总副数 + 每副天数 + 开始日期）。
 * 关系：1 阶段 → N 牙套副（{@link Aligner}）、N 条时光轴记录（{@link TimelineRecord}）。
 * 列表顺序由 {@code sortOrder} 控制；{@code status} 存 {@link com.chiji.enums.StageStatusEnum} 的 name()，
 * 「上次编辑」展示直接取 {@code updatedAt}。
 */
@Getter
@Setter
@ToString
@TableName("stage")
public class Stage {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（联合索引 idx_stage_user 前缀） */
    private Long userId;

    /** 记录模式：RecordModeEnum.name()（CLEAR_SINGLE 单模 / CLEAR_DUAL 双模），阶段建时固化，模式间数据彼此独立 */
    private String mode;

    /** 阶段名称（形如「第一阶段 · 主力矫正」，或新建时自定义/选择建议） */
    private String name;

    /** 总副数（单模 = 总副数；双模 = 总组数，实际节点数 = 组数 × 2） */
    private Integer count;

    /** 每副佩戴天数（单模；形如 7；总周期 = count × daysPerAligner，不落库） */
    private Integer daysPerAligner;

    /** 双模：软膜佩戴天数（每组前段，牙齿初步移动） */
    private Integer softDays;

    /** 双模：硬膜佩戴天数（每组后段，同序号，力量更强） */
    private Integer hardDays;

    /** 开始日期（可空；对应「开启新阶段」表单的日期开关） */
    private LocalDate startDate;

    /** 阶段状态：StageStatusEnum.name()，ACTIVE(启用)/ENDED(结束)，同一时间至多一个 ACTIVE（新建默认 ACTIVE） */
    private String status;

    /** 排序权重，越小越靠前（阶段卡片展示顺序） */
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
