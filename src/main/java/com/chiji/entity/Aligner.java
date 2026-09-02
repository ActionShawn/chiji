// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\Aligner.java
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
 * 牙套副实体（阶段下的迷你时间轴节点）。
 * <p>
 * 对应 {@code alignerNodes}：每副的状态、佩戴起止日期与当前进度。
 * 关系：N 副 → 1 阶段（{@link Stage}）；1 副 → N 条时光轴记录（{@link TimelineRecord}）。
 * 进度百分比 {@code pct} 与「第X/Y天」展示由 {@code currentDay}/{@code totalDays} 派生，不落库。
 */
@Getter
@Setter
@ToString
@TableName("aligner")
public class Aligner {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属阶段 ID（联合索引 idx_aligner_stage 前缀） */
    private Long stageId;

    /** 副序号（第X副，阶段内从 1 递增，唯一约束见索引；双模下全局连续：软奇硬偶） */
    private Integer num;

    /** 膜片类型：AlignerFilmEnum.name()（SOFT 软膜 / HARD 硬膜），仅双模阶段有值，单模为 null */
    private String filmType;

    /** 佩戴状态：AlignerStateEnum.name()，DONE/ACTIVE/FUTURE */
    private String state;

    /** 佩戴开始日期（未开始为 null；对应展示「03.10–03.16」） */
    private LocalDate startDate;

    /** 佩戴结束日期（进行中/未开始为 null） */
    private LocalDate endDate;

    /** 当前佩戴至第几天（仅 ACTIVE 有值，对应 day） */
    private Integer currentDay;

    /** 应佩戴总天数（默认取所属阶段每副天数，冗余便于单副查询，对应 total） */
    private Integer totalDays;

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
