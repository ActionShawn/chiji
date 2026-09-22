package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 小齿档案月视图（自绘月历数据源）。
 *
 * @param month               月份 yyyy-MM
 * @param visits              本月全部日程/记录（PLANNED + DONE）
 * @param expectedEndDate     预计戴完日（隐形最终副计划结束日；非隐形或非最终副为 null）
 * @param finalAlignerWearing 当前是否佩戴最终副（隐形专用；预约卡渲染依据）
 */
@Schema(description = "小齿档案月视图")
public record ClinicMonthVO(
        String month,
        List<ClinicVisitVO> visits,
        LocalDate expectedEndDate,
        Boolean finalAlignerWearing) {
}
