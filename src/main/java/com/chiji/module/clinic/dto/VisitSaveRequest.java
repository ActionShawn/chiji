package com.chiji.module.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 创建/编辑复诊日程请求（PLANNED）。
 *
 * @param visitDate        复诊日期（必填）
 * @param clinicName       诊所/医院名称（可空，前端预填上次值）
 * @param doctorName       医生姓名（可空）
 * @param remark           备注（可空，用户自由填写）
 * @param remindOffsetDays 就诊提醒提前天数 0~3（可空=null 用用户默认配置）
 */
@Schema(description = "创建/编辑复诊日程请求")
public record VisitSaveRequest(
        @Schema(description = "复诊日期") @NotNull LocalDate visitDate,
        @Schema(description = "诊所/医院名称") String clinicName,
        @Schema(description = "医生姓名") String doctorName,
        @Schema(description = "备注") String remark,
        @Schema(description = "就诊提醒提前天数 0~3（空=用默认配置）")
        @Min(value = 0, message = "提醒提前天数不合法") @Max(value = 3, message = "提醒提前天数不合法")
        Integer remindOffsetDays) {
}
