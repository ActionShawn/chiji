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
 * 用户订阅消息额度实体（本地记账，记账模型按场景分流，见
 * {@link com.chiji.module.message.service.SubscribeQuotaService}）。
 * <p>
 * 微信不提供「剩余可下发次数」查询接口，一次性订阅消息的额度只能由后端自行记账：
 * <ul>
 *   <li>常驻场景（ALIGNER_CHANGE / CLINIC_VISIT_REMIND / CLINIC_BOOK_REMIND）：生命周期累计——
 *       前端 {@code wx.requestSubscribeMessage} 返回 {@code accept} 时 {@code remain + 1}，
 *       微信下发成功（{@code errcode == 0}）时 {@code remain - 1}，下发失败保留额度；
 *       {@code acceptedTotal} 为生命周期累计授权数（仅统计用），{@code quotaDate} 恒为 {@code null}</li>
 *   <li>TAKEOFF_TIMEOUT（摘下超时提醒）：当日预扣模型——授权 +1 记入当日，下发<b>尝试即预扣</b>
 *       （{@code remain - 1}，下发失败<b>不回补</b>），跨自然日（Asia/Shanghai）随 {@code quotaDate}
 *       惰性清零；{@code acceptedTotal} 语义为<b>当日累计授权数</b>（授权调起判断口径：
 *       当日累计授权数 &lt; 每日上限，2026-10-08 C1 拍板）</li>
 * </ul>
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

    /** 订阅场景：{@link com.chiji.enums.WearReminderTypeEnum} 的 code，或 TAKEOFF_TIMEOUT（摘下超时提醒） */
    private String scene;

    /** 剩余可下发次数：常驻场景生命周期累计（accept +1 / 发送成功 -1，失败不扣）；
     * TAKEOFF_TIMEOUT 为当日剩余（尝试即扣、失败不回补、跨日惰性清零） */
    private Integer remain;

    /** 累计授权次数：常驻场景为生命周期累计（仅统计用）；TAKEOFF_TIMEOUT 为当日累计授权数 */
    private Integer acceptedTotal;

    /** 最近一次授权时间 */
    private LocalDateTime lastAcceptAt;

    /** 额度记账日期（Asia/Shanghai，惰性每日清零标记）：{@code null} = 需重置（存量行 / 未记账）。
     * 仅 TAKEOFF_TIMEOUT 场景使用，其余场景恒为 {@code null} */
    private LocalDate quotaDate;

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
