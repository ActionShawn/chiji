package com.chiji.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员回复反馈请求。
 */
public record AdminReplyFeedbackRequest(

        /** 回复内容（必填，≤500 字） */
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 500, message = "回复内容最多500字")
        String reply) {
}
