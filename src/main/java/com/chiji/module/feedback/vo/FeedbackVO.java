package com.chiji.module.feedback.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈 VO。
 * <p>
 * 供「我的反馈」历史列表展示：正文、图片、联系方式（脱敏按需）、处理状态与提交时间。
 */
public record FeedbackVO(
        /** 反馈 ID */
        Long id,
        /** 意见正文 */
        String content,
        /** 联系方式类型：PHONE / EMAIL（未留联系方式为 null） */
        String contactType,
        /** 联系方式值（手机号或邮箱，可空） */
        String contact,
        /** 处理状态：FeedbackStatusEnum.name() */
        String status,
        /** 处理状态中文标签（PENDING=待处理 / PROCESSED=已处理） */
        String statusLabel,
        /** 图片地址列表（无图片为空列表） */
        List<String> images,
        /** 提交时间 */
        LocalDateTime createdAt) {
}
