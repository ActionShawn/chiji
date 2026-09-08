package com.chiji.module.wear.dto;

/**
 * 打卡请求。
 *
 * @param action 动作：WEAR_ON（我戴上了）/ WEAR_OFF（我摘下了）
 */
public record WearPunchRequest(String action) {
}
