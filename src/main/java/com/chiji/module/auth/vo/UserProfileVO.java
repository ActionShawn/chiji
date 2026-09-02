package com.chiji.module.auth.vo;

import java.time.LocalDateTime;

/**
 * 用户个人资料 VO。
 * <p>
 * 供「我的」页展示与资料编辑回显。昵称/头像为空时由前端回退「微信用户」/默认头像。
 */
public record UserProfileVO(
        /** 用户 ID */
        Long id,
        /** 昵称（可能为 null） */
        String nickname,
        /** 头像 URL（可能为 null） */
        String avatarUrl,
        /** 手机号（可选，未绑定为 null） */
        String phone,
        /** 治疗方式：TreatmentTypeEnum.name()（未选择为 null） */
        String treatmentType,
        /** 注册时间 */
        LocalDateTime createdAt) {
}
