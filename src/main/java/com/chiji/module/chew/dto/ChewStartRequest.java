package com.chiji.module.chew.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 开始咬胶请求（#1）。
 * <p>
 * {@code targetSec} 的范围校验交由 service（越界统一抛 {@code CHEW_PARAM_INVALID}），
 * 此处仅做结构性校验。
 *
 * @param targetSec       单次目标时长（秒，可空 = 默认 600）
 * @param clientSessionId 前端幂等键（唯一键 uk_chew_client_session，防重复开始/补报）
 * @param mode            当前记录模式（CLEAR_SINGLE / CLEAR_DUAL）：用于把会话归属到
 *                        「当前选择阶段的当前副」，不跨模式误配；为空则不区分模式
 */
public record ChewStartRequest(
        Integer targetSec,
        @NotBlank(message = "clientSessionId 不能为空")
        @Size(max = 64, message = "clientSessionId 过长")
        String clientSessionId,
        String mode
) {
}
