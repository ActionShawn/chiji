package com.chiji.common.core.dto;

import lombok.Data;

/**
 * 通用「日期-数值」投影行（管理端看板按日聚合查询用）。
 * <p>
 * MyBatis 自动映射：列别名 date / value 对应驼峰字段。
 */
@Data
public class DayValueRow {

    /** 日期（DATE） */
    private java.time.LocalDate date;

    /** 聚合值（计数/求和，无记录日期由 service 补 0） */
    private Long value;
}
