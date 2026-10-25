package com.chiji.module.rubberband.entity;

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
 * 橡皮筋医嘱实体（首页改版 v2.0 BandPage「当前医嘱」卡数据源）。
 * <p>
 * 单用户单行（唯一键 {@code uk_rbp_user}），保存走 upsert；全字段可空（未填医嘱即无卡内容）。
 * 医嘱为医生给的口头/书面佩戴指导（牵引类型 / 佩戴侧 / 佩戴位置 / 每日目标次数），
 * 纯个人记录，无诊断/疗效语义，零提醒零订阅推送。
 */
@Getter
@Setter
@ToString
@TableName("rubber_band_prescription")
public class RubberBandPrescription {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（唯一键 uk_rbp_user 前缀） */
    private Long userId;

    /** 牵引类型（如「Ⅱ类牵引」，可空） */
    private String tractionType;

    /** 佩戴侧（左右两侧 / 仅左侧 / 仅右侧，可空） */
    private String side;

    /** 佩戴位置（如「上颌尖牙→下颌第一磨牙」，可空） */
    private String position;

    /** 每日目标次数（正整数 1-20，可空；BandPage 环目标与「还差 N 次」据此派生） */
    private Integer dailyTarget;

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
