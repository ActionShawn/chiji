package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 「佩戴」页工作台今日视图（/api/wear/today）。
 *
 * @param date           自然日 yyyy-MM-dd
 * @param goalSec        目标秒（超时/无效自动回默认 20h）
 * @param wearSec        今日佩戴秒（manual + makeup + 佩戴中，实时）
 * @param manualSec      打卡与晨起补记归属今日的秒
 * @param makeupSec      校准补录归属今日的秒（补录过去日，正常为 0）
 * @param wearLabel      0.1h 精度展示文案
 * @param pct            进度 0–100
 * @param full           是否已达目标
 * @param partial        是否部分达标（未 full 但 ≥ goal−4h）
 * @param newlyFull      本次动作是否刚触发达标庆祝（一次性，仅写操作后出现）
 * @param consecutiveDays 连续达标天数（截至昨天已结算，实时）
 * @param wearing        是否佩戴中
 * @param wearingSince   佩戴中起始 epoch 毫秒
 * @param guardWearing   佩戴时长守护提示（佩戴中 ≥ 20h 为 true）
 * @param sessions       今日会话时间线（含佩戴中的进行中段）
 */
public record TodayWearVO(
        String date,
        Integer goalSec,
        Long wearSec,
        Long manualSec,
        Long makeupSec,
        String wearLabel,
        Double pct,
        Boolean full,
        Boolean partial,
        Boolean newlyFull,
        Integer consecutiveDays,
        Boolean wearing,
        Long wearingSince,
        Boolean guardWearing,
        List<TodaySessionVO> sessions
) {
}
