package com.chiji.module.wear.dto;

/**
 * 佩戴会话编辑请求（只允许改开始/结束时间，不允许改 source 等其它字段）。
 * <p>
 * 时间方向不限。已结束会话 start/end 均必填且保持同日；佩戴中会话（ended_at 为 null）
 * 仅允许改 {@code start}，{@code end} 必须为空。
 *
 * @param start 新的戴上时间 HH:mm
 * @param end   新的摘下时间 HH:mm（佩戴中会话必须为 null/不传）
 */
public record WearSessionEditRequest(String start, String end) {
}