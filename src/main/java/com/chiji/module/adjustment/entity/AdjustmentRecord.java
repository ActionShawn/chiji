package com.chiji.module.adjustment.entity;

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
 * 复诊调整记录实体（固定托槽第 1 批，REQ-FB-004）。
 * <p>
 * 承接复诊调整事件：每次复诊医生换弓丝 / 加力 / 调整皮筋钩等，结构化记录弓丝序号、
 * 加力内容标签、调整日期与备注。与时光轴「同事务双写」联动：
 * {@code timelineRecordId} 持有对应的时光轴记录 ID（badge_type=ADJUSTMENT，text 存标签拼接摘要），
 * 单向关联即可支撑双向删除联动（评审 4.1 要点）：
 * <ul>
 *   <li>时光轴侧删 timeline_record → 按 timeline_record_id 反查本表一并逻辑删除</li>
 *   <li>列表侧删本表 → 先逻辑删除其 timeline_record_id 指向的时光轴记录再删自身</li>
 * </ul>
 * 归属阶段壳（stage_id 必填，阶段建时固化 mode=FIXED_BRACKET），完全不触碰 aligner 与换副 reconcile。
 */
@Getter
@Setter
@ToString
@TableName("adjustment_record")
public class AdjustmentRecord {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（idx_adj_user_date 前缀） */
    private Long userId;

    /** 归属阶段 ID（必填，阶段壳归属） */
    private Long stageId;

    /** 联动时光轴记录 ID（同事务双写；idx_adj_tr 反查，双向删除联动走此列） */
    private Long timelineRecordId;

    /** 弓丝序号（正整数，可空：医生未说明时友好留空） */
    private Integer wireNo;

    /** 加力内容标签 JSON 数组字符串（取值：换弓丝/加力/调整皮筋钩/其他） */
    private String contents;

    /** 选「其他」时的自定义文本（可空） */
    private String contentExtra;

    /** 调整日期（≤今日；补记不限时间窗，仅禁止未来日期） */
    private LocalDate adjustDate;

    /** 备注（可空） */
    private String note;

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
