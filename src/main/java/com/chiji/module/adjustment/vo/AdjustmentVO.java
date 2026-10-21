package com.chiji.module.adjustment.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 复诊调整记录展示 VO（REQ-FB-004）。
 * <p>
 * 列表页条目卡、首页固定卡调整历史轴（summary 面板）与保存成功回包共用。
 * 雪花 ID 经 JacksonConfig 全局 Long→String 序列化，全链路字符串透传，禁止前端转 Number。
 */
@Getter
@Builder
public class AdjustmentVO {

    /** 记录 ID（雪花，字符串透传） */
    private final Long id;

    /** 归属阶段 ID */
    private final Long stageId;

    /** 归属阶段名称（列表条目与首页 meta 展示用，可空：阶段已删除时为 null） */
    private final String stageName;

    /** 弓丝序号（可空，无序号时前端不渲染弓丝 chip） */
    private final Integer wireNo;

    /** 加力内容标签列表（如 ["换弓丝","加力"]） */
    private final List<String> contents;

    /** 选「其他」时的自定义文本（可空） */
    private final String contentExtra;

    /** 调整日期（ISO yyyy-MM-dd） */
    private final LocalDate adjustDate;

    /** 备注（可空） */
    private final String note;

    /** 创建时间（同日多条记录按创建时间倒序的排序依据） */
    private final LocalDateTime createdAt;
}
