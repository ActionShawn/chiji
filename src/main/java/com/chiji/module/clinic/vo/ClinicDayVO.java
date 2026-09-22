package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 小齿档案日视图（点选日历某天展示当日条目）。
 *
 * @param date     日期 yyyy-MM-dd
 * @param visits   当日日程/记录
 * @param expenses 当日花费流水
 */
@Schema(description = "小齿档案日视图")
public record ClinicDayVO(
        LocalDate date,
        List<ClinicVisitVO> visits,
        List<ClinicExpenseVO> expenses) {
}
