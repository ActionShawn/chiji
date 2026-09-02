package com.chiji.module.stage.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 时光轴记录展示 VO。
 * <p>
 * 保存成功后返回前端，供弹层关闭 toast 与首页局部刷新使用。
 */
@Getter
@Builder
public class RecordVO {

    /** 记录 ID */
    private final Long id;

    /** 所属阶段 ID */
    private final Long stageId;

    /** 所属牙套副 ID（可空） */
    private final Long alignerId;

    /** 所属阶段名称（时光轴分组展示，如「矫正一」） */
    private final String stageName;

    /** 牙套副序号（第 N 副，1-based，无关联副时为 null） */
    private final Integer alignerNum;

    /** 内容类型：TEXT/IMAGE/VIDEO/MIXED */
    private final String mediaKind;

    /** 记录正文 */
    private final String text;

    /** 记录日期（ISO 格式 yyyy-MM-dd） */
    private final LocalDate recordDate;

    /** 记录创建时间（精确到时分，时光轴展示用，如 2026-08-23T14:30:00） */
    private final LocalDateTime createdAt;

    /** 佩戴时长（小时，可空） */
    private final Integer wearHours;

    /** 特殊徽标类型（可空） */
    private final String badgeType;

    /** 标签名列表（如 ["换牙套", "小进步"]） */
    private final List<String> tags;

    /** 结构化媒体列表（含类型/时长/排序，供前端渲染与 AI 多模态上下文按类型分类） */
    private final List<MediaItemVO> medias;

    /** 媒体 URL 列表（向后兼容，从 medias 派生） */
    private final List<String> mediaUrls;
}
