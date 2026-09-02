package com.chiji.module.stage.dto;

import java.time.LocalDate;

/**
 * 单副牙套时间调整请求（锚点式联动）。
 * <p>
 * 两个字段均可为空，但至少传一个：
 * <ul>
 *   <li>仅 {@code startDate}：该副天数不变，endDate 自动 = startDate + totalDays - 1</li>
 *   <li>仅 {@code endDate}：该副天数变化，totalDays 自动 = endDate - startDate + 1</li>
 *   <li>两者都传：按给定起止调整该副天数</li>
 * </ul>
 * 修改后该副之后的全部副会按各自天数自动顺延，保证时间不重叠。
 */
public record AlignerTimeUpdateRequest(
        LocalDate startDate,
        LocalDate endDate
) {
}
