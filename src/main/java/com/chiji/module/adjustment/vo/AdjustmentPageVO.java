package com.chiji.module.adjustment.vo;

import java.util.List;

/**
 * 复诊调整分页列表 VO（GET /api/adjustments，REQ-FB-004）。
 *
 * @param total 符合条件的总条数
 * @param page  当前页码（1-based）
 * @param size  每页条数
 * @param items 当前页记录列表（adjust_date 倒序、同日创建时间倒序）
 */
public record AdjustmentPageVO(
        Long total,
        Integer page,
        Integer size,
        List<AdjustmentVO> items
) {
}
