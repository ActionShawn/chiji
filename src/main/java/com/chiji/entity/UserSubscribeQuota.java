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
 * 用户订阅消息额度实体（本地记账）。
 * <p>
 * 微信不提供「剩余可下发次数」查询接口，一次性订阅消息的额度只能由后端自行累加：
 * 前端 {@code wx.requestSubscribeMessage} 返回 {@code accept} 时 {@code remain + 1}，
 * 微信下发成功（{@code errcode == 0}）时 {@code remain - 1}；下发失败保留额度。
 * 约束：{@code (userId, scene)} 唯一（UNIQUE INDEX：uk_sub_quota_user_scene）。
 */
@Getter
@Setter
@ToString
@TableName("user_subscribe_quota")
public class UserSubscribeQuota {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（唯一索引 uk_sub_quota_user_scene 前缀） */
    private Long userId;

    /** 订阅场景：{@link com.chiji.enums.WearReminderTypeEnum} 的 code（当前仅 ALIGNER_CHANGE） */
    private String scene;

    /** 本地记账剩余可下发次数（accept +1，发送成功 -1） */
    private Integer remain;

    /** 累计授权次数（accept 次数，仅统计用） */
    private Integer acceptedTotal;

    /** 最近一次授权时间 */
    private LocalDateTime lastAcceptAt;

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
