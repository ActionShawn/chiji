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
 * 意见反馈实体。
 * <p>
 * 对应「我的 → 意见信箱」。支持正文 + 图片（最多 3 张）+ 可选联系方式（手机号/邮箱）。
 * 关系：1 反馈 → N 反馈图片（{@link FeedbackImage}）。
 * {@code status} 预留状态机，{@code reply}/{@code repliedAt} 为管理员回复预留字段。
 */
@Getter
@Setter
@ToString
@TableName("feedback")
public class Feedback {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 提交用户 ID（登录态注入，不信任前端） */
    private Long userId;

    /** 意见正文（纯文本） */
    private String content;

    /** 联系方式类型：FeedbackContactTypeEnum.name()，PHONE/EMAIL，未留联系方式为 null */
    private String contactType;

    /** 联系方式值（手机号或邮箱，可空） */
    private String contact;

    /** 处理状态：FeedbackStatusEnum.name()，PENDING(待处理)/PROCESSED(已处理) */
    private String status;

    /** 管理员回复（预留，暂未暴露给前端） */
    private String reply;

    /** 回复时间（预留） */
    private LocalDateTime repliedAt;

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
