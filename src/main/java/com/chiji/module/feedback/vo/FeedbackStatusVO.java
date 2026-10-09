package com.chiji.module.feedback.vo;

/**
 * 反馈状态 VO：闭环/撤回等状态操作的结果。
 */
public record FeedbackStatusVO(
        /** 状态：FeedbackStatusEnum.name() */
        String status,
        /** 状态中文标签 */
        String statusLabel,
        /** 撤回窗口剩余秒数（仅闭环时返回，其余为 null；前端 toast 倒计时用） */
        Long revertSeconds) {
}
