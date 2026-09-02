package com.chiji.module.stage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建记录请求体。
 * <p>
 * 对应 record-sheet 弹层「保存」提交的数据。stageId / alignerId 由前端从首页上下文传入。
 * medias 为先上传文件后拿到的 url 列表（可空，为空表示纯文字记录）。
 */
public record CreateRecordRequest(

        /** 所属阶段 ID（必填，记录归属阶段） */
        Long stageId,

        /** 所属牙套副 ID（可空，无 active 副时为 null） */
        Long alignerId,

        /** 记录正文（必填，3-300 字） */
        @NotBlank(message = "正文不能为空")
        @Size(min = 3, max = 300, message = "正文3-300字")
        String text,

        /** 佩戴时长（小时，可空） */
        Integer wearHours,

        /** 标签名列表（可空，最多 6 个；自定义标签会自动创建） */
        @Size(max = 6, message = "最多6个标签")
        List<String> tags,

        /** 媒体列表（可空，最多 6 项；为空表示纯文字记录。url 由先调上传接口获取） */
        @Size(max = 6, message = "最多6个媒体")
        @Valid
        List<MediaItem> medias
) {
}
