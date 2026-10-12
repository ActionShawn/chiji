package com.chiji.module.chew.dto;

/**
 * 咬胶状态变更请求（#2–#5 暂停/继续/重来/结束）。
 * <p>
 * 「+1 分钟」等目标调整随任一次状态变更上送，后端以最后上报值覆盖落库；
 * 不传则不改变既有目标。请求体可省略（仅调整目标时才需要）。
 *
 * @param targetSec 单次目标时长（秒，可空 = 沿用既有）
 */
public record ChewStateChangeRequest(Integer targetSec) {
}
