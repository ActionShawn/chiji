package com.chiji.module.stage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建记录请求体。
 * <p>
 * 对应 record-sheet 弹层「保存」提交的数据。stageId / alignerId 由前端从首页上下文传入。
 * medias 为先上传文件后拿到的 url 列表（可空，为空表示纯文字记录）。
 * <p>
 * 疼痛记录（REQ-FB-007，第 1 批仅固定托槽开放）：painScore 必填 0-5、painPart 可选；
 * 携带疼痛字段时服务层校验 treatment_type 门控与 wearHours 互斥。
 * 托槽脱落记录（首页改版 v2.0）：bracketIssue 必填（预设四选一）、bracketTooth 可选（牙位）；
 * 与疼痛同构的固定托槽专属类型，正文可空（空时派生中性摘要）。
 * text 校验由 DTO @NotBlank/@Size 迁移至服务层条件校验（BE-FB-06：疼痛记录正文可空，
 * 空时落库派生摘要；普通记录保持与原注解等价的强校验，隐形路径行为不变）。
 *
 * @param stageId      所属阶段 ID（必填，记录归属阶段）
 * @param alignerId    所属牙套副 ID（可空，无 active 副时为 null）
 * @param text         记录正文（普通记录必填 3-300 字；疼痛/托槽脱落记录可空，服务层兜底派生摘要）
 * @param wearHours    佩戴时长（小时，可空；疼痛/托槽脱落记录禁传，互斥校验）
 * @param painScore    疼痛程度 0-5（可空；非空即判定为疼痛记录，需过门控）
 * @param painPart     疼痛部位标签列表（可空，每项 ≤20 字；预设 上排/下排/左侧/右侧/牙龈/脸颊内侧/舌头 + 自定义）
 * @param bracketIssue 托槽脱落情形（可空；非空即判定为托槽脱落记录，需过门控）
 * @param bracketTooth 脱落牙位（可空，如「右上4」）
 * @param tags         标签名列表（可空，最多 6 个；自定义标签会自动创建）
 * @param medias       媒体列表（可空，最多 6 项；为空表示纯文字记录。url 由先调上传接口获取）
 */
public record CreateRecordRequest(

        /** 所属阶段 ID（必填，记录归属阶段） */
        Long stageId,

        /** 所属牙套副 ID（可空，无 active 副时为 null） */
        Long alignerId,

        /** 记录正文（普通记录必填 3-300 字，服务层校验；疼痛/托槽脱落记录可空） */
        String text,

        /** 佩戴时长（小时，可空；疼痛/托槽脱落记录禁传） */
        Integer wearHours,

        /** 疼痛程度 0-5（可空；非空即判定为疼痛记录，需过固定托槽门控） */
        Integer painScore,

        /** 疼痛部位标签列表（可空，每项 ≤20 字） */
        @Size(max = 10, message = "部位标签最多10个")
        List<String> painPart,

        /** 托槽脱落情形（可空；非空即判定为托槽脱落记录，需过固定托槽门控） */
        String bracketIssue,

        /** 脱落牙位（可空，如「右上4」） */
        String bracketTooth,

        /** 标签名列表（可空，最多 6 个；自定义标签会自动创建） */
        @Size(max = 6, message = "最多6个标签")
        List<String> tags,

        /** 媒体列表（可空，最多 6 项；为空表示纯文字记录。url 由先调上传接口获取） */
        @Size(max = 6, message = "最多6个媒体")
        @Valid
        List<MediaItem> medias
) {

    /** 托槽脱落情形的预设取值（记录分类用语，非医疗诊断；与前端 mode-config.BRACKET_ISSUES 同源）。 */
    public static final List<String> BRACKET_ISSUES =
            List.of("托槽完全脱落", "托槽松动", "弓丝移位或扎嘴", "结扎丝脱落");
}
