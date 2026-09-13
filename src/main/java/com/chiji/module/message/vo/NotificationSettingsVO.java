package com.chiji.module.message.vo;

import java.util.List;

/**
 * 通知设置整读 VO。
 *
 * @param master              通知总开关（false 时不再生成/归档提醒）
 * @param popup               弹窗提醒开关（false 时提醒仅信箱可见）
 * @param badge               信箱红点/角标开关
 * @param preset              当前预设模式（STANDARD / IMPORTANT_ONLY / CUSTOM）
 * @param dnd                 勿扰时段设置（仅抑制即时弹窗，信箱消息照常归档）
 * @param subscribeAvailable  订阅消息能力是否可用（模板 ID 配置非空即 true，前端据此启用订阅通道）
 * @param types               8 类提醒开关明细（含默认值，未手工拨动过也全量下发）
 * @param alignerRemindTime   换副提醒时间（整点 {@code HH:00}，缺省 {@code 07:00}）
 * @param alignerRemindOffset 换副提醒时机偏移：-1 到期前一天 / 0 到期当天 / 1 到期后一天
 * @param subscribeRemain     一次性订阅剩余可下发次数（非订阅用户为 0）
 */
public record NotificationSettingsVO(
        Boolean master,
        Boolean popup,
        Boolean badge,
        String preset,
        DndSettingVO dnd,
        Boolean subscribeAvailable,
        List<TypeSwitchVO> types,
        String alignerRemindTime,
        Integer alignerRemindOffset,
        Integer subscribeRemain
) {
}
