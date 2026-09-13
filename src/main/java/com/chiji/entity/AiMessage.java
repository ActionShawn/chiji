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
 * AI 问答消息实体。
 * <p>
 * 对应 {@code ai_message}：会话内逐条落库（user 提问 / assistant 回复），
 * 按会话升序回放（联合索引 idx_ai_msg_conv）。{@code userId} 冗余存储，
 * 供会话权限校验与按用户整体清理；暂不支持图片等多模态内容。
 */
@Getter
@Setter
@ToString
@TableName("ai_message")
public class AiMessage {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话 ID（联合索引 idx_ai_msg_conv 前缀） */
    private Long conversationId;

    /** 所属用户 ID（冗余，权限校验与清理用） */
    private Long userId;

    /** 角色：user(提问) / assistant(回复) */
    private String role;

    /** 消息内容 */
    private String content;

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
