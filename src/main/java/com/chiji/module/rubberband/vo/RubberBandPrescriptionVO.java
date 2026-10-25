package com.chiji.module.rubberband.vo;

/**
 * 橡皮筋医嘱展示 VO（GET /api/rubber-band/prescription，BandPage「当前医嘱」卡）。
 *
 * @param tractionType 牵引类型（如「Ⅱ类牵引」，可空）
 * @param side         佩戴侧（左右两侧 / 仅左侧 / 仅右侧，可空）
 * @param position     佩戴位置（如「上颌尖牙→下颌第一磨牙」，可空）
 * @param dailyTarget  每日目标次数（1-20，可空）
 * @param updatedDate  更新时间文案（「MM.dd」，如「10.22」；空 = 从未设置）
 */
public record RubberBandPrescriptionVO(
        String tractionType,
        String side,
        String position,
        Integer dailyTarget,
        String updatedDate
) {
}
