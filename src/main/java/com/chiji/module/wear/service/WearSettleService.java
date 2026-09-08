package com.chiji.module.wear.service;

import com.chiji.entity.WearDailySummary;

import java.time.LocalDate;

/**
 * 佩戴每日结算服务：把某用户某<b>完整自然日</b>（Asia/Shanghai）的会话切分归并，
 * 物化到 {@code wear_daily_summary}，供连续达标统计与首页只读查询。
 * <p>
 * 结算<b>不覆盖已结算日</b>（历史达标口径稳定，目标取结算当日快照），仅补录（重算某日）
 * 时允许覆写；参见 {@link #settleDay} 与 {@link #settleMissing}。
 */
public interface WearSettleService {

    /**
     * 强制结算某日（重算并 upsert，覆盖已有行）。
     * <p>
     * 用于「校准/补录」回填某历史日后重算该日；也会生成没有任何会话的 0 结果行。
     * 调用方需保证 date 是完整自然日（通常 ≤ 昨天）。
     *
     * @param userId 用户 ID
     * @param date   完整自然日
     * @return 结算后行
     */
    WearDailySummary settleDay(Long userId, LocalDate date);

    /**
     * 缺失即结算某日：已有行时保持原样（不覆盖快照），否则与 {@link #settleDay} 相同。
     * <p>
     * 供 00:30 定时 job 与客户端懒结算（00:05 后打开）幂等兜底。
     *
     * @param userId 用户 ID
     * @param date   完整自然日
     * @return 已存在的行或新结算的行
     */
    WearDailySummary settleMissing(Long userId, LocalDate date);

    /**
     * 确保昨天已结算（客户端懒结算）。当前时间早于次日 00:05 时不动作（昨日可能仍在续戴），
     * 之后若昨天尚未结算则补结算；已有则跳过。
     *
     * @param userId 用户 ID
     */
    void ensureYesterdaySettled(Long userId);
}
