package com.chiji.module.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订阅授权结果上报请求。
 *
 * @param result 前端 {@code wx.requestSubscribeMessage} 结果：{@code accept} 已同意 / {@code reject} 未同意
 * @param scene  订阅场景（小齿档案共用端点用：CLINIC_VISIT_REMIND / CLINIC_BOOK_REMIND；换副端点忽略此字段）
 */
@Schema(description = "订阅授权结果上报")
public record SubscribeRecordRequest(
        @Schema(description = "授权结果：accept / reject") String result,
        @Schema(description = "订阅场景（复诊类共用端点必传）") String scene) {
}
