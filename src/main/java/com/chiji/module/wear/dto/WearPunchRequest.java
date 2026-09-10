package com.chiji.module.wear.dto;

/**
 * 打卡请求。
 *
 * @param action 动作：WEAR_ON（我戴上了）/ WEAR_OFF（我摘下了）
 * @param mode   当前记录模式（CLEAR_SINGLE / CLEAR_DUAL）：WEAR_ON 时据此把会话归属到
 *               「当前选择阶段的当前副」，不跨模式误配；为空则不区分模式
 */
public record WearPunchRequest(String action, String mode) {
}
