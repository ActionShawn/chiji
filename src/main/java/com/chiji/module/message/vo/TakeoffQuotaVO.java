package com.chiji.module.message.vo;

/**
 * 摘下超时提醒当日额度 VO（前端已按此结构占位，字段名勿改）。
 *
 * @param todayAccepted 当日累计授权数（授权调起判断口径：{@code todayAccepted < dailyLimit} 时可调起）
 * @param remain        当日剩余可下发额度（通知设置页「今日剩余提醒次数」展示用）
 * @param dailyLimit    每日授权上限（当前 3）
 */
public record TakeoffQuotaVO(
        int todayAccepted,
        int remain,
        int dailyLimit
) {
}
