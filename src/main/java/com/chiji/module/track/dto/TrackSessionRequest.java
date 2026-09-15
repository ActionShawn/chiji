package com.chiji.module.track.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 使用会话上报请求（App 级埋点）。
 */
public record TrackSessionRequest(

        /** 会话进入时刻（毫秒时间戳，Asia/Shanghai 解析） */
        @NotNull(message = "enterAt 不能为空")
        Long enterAt,

        /** 停留秒数（1 ~ 172800） */
        @NotNull(message = "durationSec 不能为空")
        @Min(value = 1, message = "durationSec 最小 1 秒")
        @Max(value = 172800, message = "durationSec 最大 48 小时")
        Integer durationSec,

        /** 前端生成幂等键（补报防重，≤64 字符） */
        @NotBlank(message = "clientSessionId 不能为空")
        @Size(max = 64, message = "clientSessionId 过长")
        String clientSessionId) {
}
