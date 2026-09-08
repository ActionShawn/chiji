package com.chiji.module.message.vo;

/**
 * 单提醒类型开关 VO（通知设置页的 8 行开关）。
 *
 * @param type    提醒类型（WearReminderTypeEnum code）
 * @param label   中文名称（标题，如「睡前佩戴提醒」）
 * @param group   所属组名（佩戴打卡 / 矫正进度），前端按组分段
 * @param enabled 是否开启
 */
public record TypeSwitchVO(
        String type,
        String label,
        String group,
        Boolean enabled
) {
}
