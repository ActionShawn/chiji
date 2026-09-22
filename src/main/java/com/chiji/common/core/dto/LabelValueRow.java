package com.chiji.common.core.dto;

import lombok.Data;

/**
 * 通用「标签-数值」投影行（管理端看板按小时/月份聚合查询用）。
 * <p>
 * MyBatis 自动映射：列别名 label / value 对应驼峰字段。
 */
@Data
public class LabelValueRow {

    /** 标签（小时两位数字「09」或月份「2026-09」） */
    private String label;

    /** 聚合值（计数/求和，无记录分组由 service 补 0） */
    private Long value;
}
