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
 * 反馈图片实体。
 * <p>
 * 对应「意见信箱」提交的图片，最多 3 张。图片地址由上传接口 {@code /api/files/upload} 返回。
 * 关系：N 图片 → 1 反馈（{@link Feedback}）。
 */
@Getter
@Setter
@ToString
@TableName("feedback_image")
public class FeedbackImage {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属反馈 ID */
    private Long feedbackId;

    /** 图片地址（COS URL） */
    private String url;

    /** 展示排序，越小越靠前 */
    private Integer sortOrder;

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
