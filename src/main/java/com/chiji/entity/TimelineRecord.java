// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\TimelineRecord.java
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
 * 时光轴记录实体（核心业务实体）。
 * <p>
 * 对应 {@code timelineRecords} 与「记录弹层」提交（文字 + 媒体 + 标签）：
 * 佩戴/心情/复诊/里程碑等一次性记录。关系：N 记录 → 1 用户、1 阶段；
 * 1 记录 → N 媒体（{@link RecordMedia}）、M 标签（经 {@link RecordTagRelation}）。
 * 以下展示字段不落库，由查询层派生：{@code side}（左右交替排版）、{@code snippet}（正文截取）、
 * {@code meta}（阶段名 · 第X副）、{@code stageColor}/{@code nodeColor}。
 */
@Getter
@Setter
@ToString
@TableName("timeline_record")
public class TimelineRecord {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（联合索引 idx_record_user_date 前缀） */
    private Long userId;

    /** 所属阶段 ID（必填） */
    private Long stageId;

    /** 所属牙套副 ID（可空：阶段切换类记录无具体副，如「准备阶段 · 完成」） */
    private Long alignerId;

    /** 媒体类型汇总：RecordKindEnum.name()，TEXT/IMAGE/VIDEO/MIXED（保存时按媒体计算） */
    private String mediaKind;

    /** 记录正文（纯文本，TEXT；前端摘要 snippet 由正文截取） */
    private String text;

    /** 记录日期（联合索引 idx_record_user_date 第二列） */
    private LocalDate recordDate;

    /** 佩戴时长（小时，可空；有值时徽标显示「22h」） */
    private Integer wearHours;

    /** 特殊徽标类型：RecordBadgeTypeEnum.name()，FOLLOW_UP/MILESTONE/STAGE_SWITCH（可空，与 wearHours 互斥） */
    private String badgeType;

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
