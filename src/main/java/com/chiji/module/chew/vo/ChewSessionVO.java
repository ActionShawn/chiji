package com.chiji.module.chew.vo;

/**
 * 咬胶会话视图（#1–#6 / #12 回显）。
 * <p>
 * 净时长 {@code netSec} = (endedAt | pausedAt | now) − startedAt − pauseSec（暂停中冻结），
 * 由服务端派生、不落库。时刻返回 epoch 毫秒，时长返回秒。
 *
 * @param id           会话 ID
 * @param stageId      开始时刻阶段快照（可空 = 未归属档）
 * @param alignerId    开始时刻牙套副快照（可空 = 未归属档）
 * @param alignerLabel 归属标签「第X副 · 第Y天」；无归属为 ""
 * @param startedAt    开始时刻 epoch 毫秒
 * @param endedAt      结束时刻 epoch 毫秒（进行中/暂停中为 null）
 * @param pauseSec     已结算累计暂停秒
 * @param netSec       净时长（秒，含进行中实时值）
 * @param running      是否运行中（未结束且未暂停）
 * @param paused       是否暂停中（未结束且已暂停）
 * @param targetSec    单次目标时长（秒）
 * @param reached      本次是否达到单次目标（netSec >= targetSec）
 */
public record ChewSessionVO(
        Long id,
        Long stageId,
        Long alignerId,
        String alignerLabel,
        Long startedAt,
        Long endedAt,
        Integer pauseSec,
        Long netSec,
        Boolean running,
        Boolean paused,
        Integer targetSec,
        Boolean reached
) {
}
