package com.chiji.module.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订阅额度视图。
 *
 * @param remain 当前剩余可下发次数（本地记账）
 */
@Schema(description = "订阅额度")
public record SubscribeQuotaVO(
        @Schema(description = "剩余可下发次数") Integer remain) {
}
