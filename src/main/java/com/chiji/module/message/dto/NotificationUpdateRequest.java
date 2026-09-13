package com.chiji.module.message.dto;

import java.util.List;

/**
 * 通知设置更新请求（局部更新语义：未提供的字段保持不变）。
 * <p>
 * 应用规则：
 * <ul>
 *   <li>{@code preset} 非空 → 应用对应预设模板到 8 类开关明细，再连同其它给定字段一起保存</li>
 *   <li>{@code types} 非空 → 逐个覆盖开关明细，并重新推导预设（与某预设完全一致则回该预设，否则 CUSTOM）</li>
 *   <li>两者都不给且仅改总开关/弹窗/红点/勿扰/换副提醒时，开关明细保持不变</li>
 * </ul>
 *
 * @param master              通知总开关（可选）
 * @param popup               弹窗提醒开关（可选）
 * @param badge               信箱红点开关（可选）
 * @param dnd                 勿扰时段（可选，内部字段亦可局部）
 * @param preset              预设模式（STANDARD / IMPORTANT_ONLY，可选）
 * @param types               逐类型开关（可选）
 * @param alignerRemindTime   换副提醒时间（可选，整点 {@code HH:00}）
 * @param alignerRemindOffset 换副提醒时机偏移（可选，-1/0/1）
 */
public record NotificationUpdateRequest(
        Boolean master,
        Boolean popup,
        Boolean badge,
        DndUpdate dnd,
        String preset,
        List<TypeSwitchUpdate> types,
        String alignerRemindTime,
        Integer alignerRemindOffset
) {

    /**
     * 勿扰时段局部更新。
     *
     * @param enabled 是否开启（可选）
     * @param start   开始时间 HH:mm（可选）
     * @param end     结束时间 HH:mm（可选）
     */
    public record DndUpdate(Boolean enabled, String start, String end) {
    }

    /**
     * 单类型开关更新。
     *
     * @param type    提醒类型（WearReminderTypeEnum code，必填）
     * @param enabled 是否开启
     */
    public record TypeSwitchUpdate(String type, Boolean enabled) {
    }
}
