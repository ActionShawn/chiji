package com.chiji.module.message.vo;

/**
 * 勿扰时段设置 VO（时间以「HH:mm」字符串下发，前端省去时区换算）。
 *
 * @param enabled 是否开启勿扰
 * @param start   开始时间（HH:mm）
 * @param end     结束时间（HH:mm）
 */
public record DndSettingVO(
        Boolean enabled,
        String start,
        String end
) {
}
