package com.chiji.module.adjustment.vo;

import java.util.List;

/**
 * 固定托槽首页复诊调整面板 VO（并入 GET /api/home/summary 响应，BE-FB-09）。
 * <p>
 * 仅 mode=FIXED_BRACKET 时返回（字段级 NON_NULL，隐形模式响应无新增字段，回归红线）。
 * 数据支撑 UI 规格书固定版主卡 Row 1（弓丝 chip / 距上次调整 X 天）、Row 2（调整历史轴最近 10 条）、
 * Row 5（累计调整 N 次）与「距下次复诊 Y 天」（小齿档案口径，零新增请求红线）。
 *
 * @param total         当前用户全部调整记录总数（Row 5「累计调整 N 次」）
 * @param nextVisitDate 下一次复诊日期（小齿档案最近一条待复诊 PLANNED 且 ≥ 今日；无则为 null）
 * @param items         最近 10 条调整记录（adjust_date 倒序，前端自行正序渲染历史轴）
 */
public record FixedAdjustmentPanelVO(
        Long total,
        java.time.LocalDate nextVisitDate,
        List<AdjustmentVO> items
) {
}
