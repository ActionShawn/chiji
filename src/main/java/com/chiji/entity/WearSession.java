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
 * 佩戴会话实体（物理佩戴段）。
 * <p>
 * 一段「戴上 → 摘下」为一行：{@code startedAt}（可能回填过去）到 {@code endedAt}（摘下时间，
 * 佩戴中为 null）。跨午夜<b>不预拆</b>，某自然日的实际佩戴量在查询/结算时按
 * {@code [当日00:00, 次日00:00)} 切分归属（天然日 = Asia/Shanghai）。
 * <p>
 * {@code source} 区分 MANUAL（手工打卡/晨起补记）与 MAKEUP（校准补录，携带
 * {@code makeupFor} 目标自然日，补录段按该日全额归属）；{@code alignerId} 为打卡时当前
 * 佩戴的牙套副（本副统计用，无 ACTIVE 副时为空）。
 */
@Getter
@Setter
@ToString
@TableName("wear_session")
public class WearSession {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（索引 idx_wear_session_user_time 前缀） */
    private Long userId;

    /** 打卡时当前佩戴的牙套副 ID（本副统计用；无 ACTIVE 副时为空） */
    private Long alignerId;

    /** 来源：WearSourceEnum.name()，MANUAL(打卡/晨起补记)/MAKEUP(校准补录) */
    private String source;

    /** 补录目标自然日（仅 source=MAKEUP 有值） */
    private LocalDate makeupFor;

    /** 戴上时间（可回填到过去；跨午夜不预拆） */
    private LocalDateTime startedAt;

    /** 摘下时间（佩戴中为 null） */
    private LocalDateTime endedAt;

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
