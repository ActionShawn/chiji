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
 * 反馈评论实体：连续对话消息（用户与运营多轮往返）。
 * <p>
 * 对应「意见反馈」对话详情页的一条消息。关系：1 反馈 → N 评论。
 * 首条消息是 {@link Feedback} 本体（content + feedback_image），评论表只存后续追加消息。
 * 旧版单条 {@code feedback.reply} 不迁入本表，线程视图按 {@code repliedAt} 合成展示。
 */
@Getter
@Setter
@ToString
@TableName("feedback_comment")
public class FeedbackComment {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属反馈 ID */
    private Long feedbackId;

    /** 发言人用户 ID：USER=提交用户；ADMIN=0（运营无用户账号） */
    private Long userId;

    /** 发言人角色：FeedbackCommentRoleEnum，USER / ADMIN */
    private String role;

    /** 评论正文（可空：允许仅图片，正文与图片至少一项非空） */
    private String content;

    /** 图片地址 JSON 数组字符串（如 ["https://..."]），无图为空/null */
    private String images;

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
