package com.chiji.module.clinic.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 复诊日程/就诊记录视图。
 *
 * @param id               记录 ID
 * @param visitDate        复诊日期
 * @param status           PLANNED / DONE
 * @param clinicName       诊所/医院名称
 * @param doctorName       医生姓名
 * @param remark           备注（可空）
 * @param content          就诊内容摘要
 * @param nextVisitDate    下次复诊日期
 * @param stageId          关联阶段 ID
 * @param alignerId        关联牙套副 ID
 * @param timelineRecordId 投影时光轴记录 ID
 * @param remindOffsetDays 就诊提醒提前天数（null=用户默认）
 * @param mediaList        完成记录媒体（仅 DONE 有值）
 */
@Schema(description = "复诊日程/就诊记录")
public record ClinicVisitVO(
        Long id,
        LocalDate visitDate,
        String status,
        String clinicName,
        String doctorName,
        String remark,
        String content,
        LocalDate nextVisitDate,
        Long stageId,
        Long alignerId,
        Long timelineRecordId,
        Integer remindOffsetDays,
        List<MediaVO> mediaList) {

    /**
     * 媒体条目。
     *
     * @param url  媒体地址
     * @param type IMAGE / VIDEO
     */
    @Schema(description = "媒体条目")
    public record MediaVO(String url, String type) {
    }
}
