package com.chiji.module.wear.vo;

/**
 * 首页聚合（HomeSummaryVO.wearTop）佩戴概览。activeAligners 与 activeAlignersNext 省略。
 *
 * @param alignerId      当前佩戴牙套副 id（无则在 null 分支；由 stage AlignerService 定位）
 * @param alignerState   当前副状态小写（active）
 * @param wearing        是否佩戴中（存在未结束的打卡会话）
 * @param wearingSince   佩戴中起始 epoch 毫秒（未佩戴为 null）
 * @param goalSec        目标秒
 * @param wearSec        今日已累计秒（实时，含佩戴中）
 * @param wearLabel      0.1h 精度的展示文案，如 "12.5h"
 * @param pct            今日进度 0–100（含小数）
 * @param full           今日是否已达目标
 */
public record WearTopVO(
        Long alignerId,
        String alignerState,
        Boolean wearing,
        Long wearingSince,
        Integer goalSec,
        Long wearSec,
        String wearLabel,
        Double pct,
        Boolean full
) {
}
