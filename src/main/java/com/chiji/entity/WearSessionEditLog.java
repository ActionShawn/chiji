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
 * 佩戴会话编辑留痕实体（{@code wear_session_edit_log}）。
 * <p>
 * 每次对佩戴会话的编辑/删除写一行，记录修改类型、修改字段、改前/改后值与积分消耗，
 * 用于「修正」操作的可追溯审计；{@code pointsCost} 当前恒为 0，积分系统启用后填写。
 */
@Getter
@Setter
@ToString
@TableName("wear_session_edit_log")
public class WearSessionEditLog {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联会话 id */
    private Long sessionId;

    /** 操作用户 ID */
    private Long userId;

    /** 操作类型：TIME_START / TIME_END / CREATE / DELETE */
    private String editType;

    /** 改动字段：started_at / ended_at */
    private String field;

    /** 修改前值 */
    private LocalDateTime beforeValue;

    /** 修改后值 */
    private LocalDateTime afterValue;

    /** 本次消耗积分（当前 0，后续积分系统填写） */
    private Integer pointsCost;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}