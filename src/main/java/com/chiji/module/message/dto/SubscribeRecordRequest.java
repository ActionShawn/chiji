package com.chiji.module.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订阅授权结果上报请求。
 *
 * @param result 前端 {@code wx.requestSubscribeMessage} 结果：{@code accept} 已同意 / {@code reject} 未同意
 */
@Schema(description = "订阅授权结果上报")
public record SubscribeRecordRequest(
        @Schema(description = "授权结果：accept / reject") String result) {
}
