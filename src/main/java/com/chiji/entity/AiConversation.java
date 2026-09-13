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
 * AI 问答会话实体。
 * <p>
 * 对应 {@code ai_conversation}：一次「问小齿」连续对话为一行，标题由首条提问截断生成；
 * 会话列表按用户游标分页（id 倒序）。删除为逻辑删除，删除后不再出现在历史列表。
 */
@Getter
@Setter
@ToString
@TableName("ai_conversation")
public class AiConversation {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（联合索引 idx_ai_conv_user 前缀） */
    private Long userId;

    /** 会话标题（首条提问截断生成，最多 64 字符） */
    private String title;

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
