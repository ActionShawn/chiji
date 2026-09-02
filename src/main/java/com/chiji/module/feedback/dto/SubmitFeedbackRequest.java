package com.chiji.module.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 提交意见请求体。
 * <p>
 * 对应「意见信箱」表单。图片地址由上传接口 {@code /api/files/upload} 先行获取后引用。
 * 联系方式选填，可留空；填写时 {@code contactType} 指定 PHONE / EMAIL，格式由后端校验。
 */
public record SubmitFeedbackRequest(

        /** 意见正文（必填，最多 500 字） */
        @NotBlank(message = "内容不能为空")
        @Size(max = 500, message = "内容最多500字")
        String content,

        /** 联系方式类型（可空）：FeedbackContactTypeEnum.name()，PHONE / EMAIL */
        String contactType,

        /** 联系方式值（可空，手机号或邮箱；与 contactType 成对出现） */
        @Size(max = 64, message = "联系方式过长")
        String contact,

        /** 图片地址列表（可空，最多 3 张） */
        @Size(max = 3, message = "最多上传3张图片")
        List<String> imageUrls
) {
}
