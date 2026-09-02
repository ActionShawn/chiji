package com.chiji.module.stage.dto;

import java.time.LocalDate;

/**
 * 换回上一副请求（撤回误换副）。
 * <p>
 * {@code endDate} 为可选字段，仅当上一副<b>已戴满周期</b>（超期后换的副）时有意义：
 * 用户在弹窗中选择「再戴几天」（含今天起算），前端折算为佩戴终点日期传入；
 * 未传时后端默认只归还今天（纯撤销，不延长）。
 * <p>
 * 上一副<b>未戴满周期</b>（提前换的副）时无需传参，后端按原计划终点（startDate + totalDays - 1）自动恢复。
 */
public record RevertAlignerRequest(
        LocalDate endDate
) {
}
