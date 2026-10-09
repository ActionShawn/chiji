package com.chiji.module.feedback.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 追加评论请求体（用户/运营共用）。
 * <p>
 * 正文与图片至少一项非空（允许仅图片）；图片地址由 {@code /api/files/upload} 先行获取后引用。
 */
public record AddCommentRequest(

        /** 评论正文（可空，最多 500 字；与 imageUrls 至少一项非空） */
        @Size(max = 500, message = "内容最多500字")
        String content,

        /** 图片地址列表（可空，最多 3 张） */
        @Size(max = 3, message = "最多上传3张图片")
        List<String> imageUrls
) {
}
