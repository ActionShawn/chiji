package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 小齿档案月视图（自绘月历数据源）。
 *
 * @param month               月份 yyyy-MM
 * @param visits              本月全部日程/记录（PLANNED + DONE）
 * @param expectedEndDate     预约窗口锚点日（隐形最终副计划结束日；非隐形或非最终副为 null，预约卡渲染依据）
 * @param finalAlignerWearing 当前是否佩戴最终副（隐形专用；预约卡渲染依据）
 * @param stageExpectedEndDate 阶段预计完成日 = 指定阶段最后一副计划结束日（stageId 为首页当前选中阶段，
 *                             缺省回退 ACTIVE 阶段；与佩戴进度无关，月历标记用；非隐形或无副数据为 null）
 */
@Schema(description = "小齿档案月视图")
public record ClinicMonthVO(
        String month,
        List<ClinicVisitVO> visits,
        LocalDate expectedEndDate,
        Boolean finalAlignerWearing,
        LocalDate stageExpectedEndDate) {
}
