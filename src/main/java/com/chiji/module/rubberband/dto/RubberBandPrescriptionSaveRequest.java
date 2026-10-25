package com.chiji.module.rubberband.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 橡皮筋医嘱保存请求体（PUT /api/rubber-band/prescription）。
 * <p>
 * 全字段可空（未填即不落对应列，或整体清空）；牵引类型/佩戴侧/佩戴位置为医生佩戴指导文本，
 * 每日目标次数为正整数（BandPage 环目标）。纯个人记录，无疗效/诊断语义。
 *
 * @param tractionType 牵引类型（≤20 字，可空）
 * @param side         佩戴侧（≤20 字，可空）
 * @param position     佩戴位置（≤50 字，可空）
 * @param dailyTarget  每日目标次数（1-20，可空）
 */
public record RubberBandPrescriptionSaveRequest(
        @Size(max = 20, message = "牵引类型不能超过 20 字") String tractionType,
        @Size(max = 20, message = "佩戴侧不能超过 20 字") String side,
        @Size(max = 50, message = "佩戴位置不能超过 50 字") String position,
        @Min(value = 1, message = "每日目标次数至少为 1") @Max(value = 20, message = "每日目标次数不超过 20") Integer dailyTarget
) {
}
