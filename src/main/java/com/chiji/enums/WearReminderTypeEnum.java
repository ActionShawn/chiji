package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 佩戴 / 通知提醒类型枚举（8 类，分两组）。
 * <p>
 * 对应 {@code message.reminder_type} 与 {@code user_notification_config.reminder_type}；
 * {@code code} 与枚举 {@code name()} 保持一致。定义分组（组名文案、信箱分类、优先级、
 * 预设默认开关），是通知设置页与消息生成的真源。
 * <ul>
 *   <li>佩戴打卡组（信箱分类 WEAR，前端沙色 chip）：状态守护 / 睡前催戴 / 结算结果 / 达标庆祝</li>
 *   <li>矫正进度组（信箱分类 SYSTEM_CARE，前端蓝色 chip）：换副 / 每日记录 / 里程碑 / 问候</li>
 * </ul>
 * 说明：
 * <ul>
 *   <li>WEAR_GUARD（状态守护 / 防挂机）只做页内弹窗提醒，<b>不写信箱消息</b></li>
 *   <li>订阅消息通道未开通：本后端不涉及订阅消息下发，消息以信箱归档为准</li>
 *   <li>STANDARD 预设 = 各类型 {@link #isDefaultOn()}；IMPORTANT_ONLY 预设仅保留
 *       ALIGNER_CHANGE + WEAR_SETTLE（按需求文档语义，非原型代码行为）</li>
 * </ul>
 */
public enum WearReminderTypeEnum implements IEnum<String> {

    /** 佩戴状态守护（防挂机，页内弹窗） */
    WEAR_GUARD("WEAR_GUARD", "佩戴状态守护", "佩戴打卡", true, 1),
    /** 睡前佩戴提醒（22:30） */
    WEAR_BEDTIME("WEAR_BEDTIME", "睡前佩戴提醒", "佩戴打卡", true, 1),
    /** 佩戴结算结果（次日 00:30，结算昨天） */
    WEAR_SETTLE("WEAR_SETTLE", "佩戴结算结果", "佩戴打卡", true, 1),
    /** 达标庆祝（今日首次达标实时触发，一天一次） */
    WEAR_CELEBRATE("WEAR_CELEBRATE", "达标庆祝", "佩戴打卡", true, 2),
    /** 换副提醒 */
    ALIGNER_CHANGE("ALIGNER_CHANGE", "换副提醒", "矫正进度", true, 1),
    /** 每日记录提醒 */
    DAILY_RECORD("DAILY_RECORD", "每日记录提醒", "矫正进度", true, 1),
    /** 里程碑提醒 */
    MILESTONE("MILESTONE", "里程碑提醒", "矫正进度", true, 1),
    /** 问候 */
    GREETING("GREETING", "问候", "矫正进度", false, 0);

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /** 所属组名（佩戴打卡 / 矫正进度），通知设置页分组展示 */
    private final String groupName;

    /** 预设默认是否开启（STANDARD 模板） */
    private final boolean defaultOn;

    /** 优先级：0 低 / 1 普通 / 2 高（达标庆祝置高） */
    private final int priority;

    WearReminderTypeEnum(String code, String desc, String groupName, boolean defaultOn, int priority) {
        this.code = code;
        this.desc = desc;
        this.groupName = groupName;
        this.defaultOn = defaultOn;
        this.priority = priority;
    }

    @Override
    public String getValue() {
        return code;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isDefaultOn() {
        return defaultOn;
    }

    public int getPriority() {
        return priority;
    }

    /** 是否佩戴打卡组（true → 信箱分类 WEAR；false → SYSTEM_CARE） */
    public boolean isWearGroup() {
        return "佩戴打卡".equals(groupName);
    }

    /**
     * 提醒类型对应的信箱分类：佩戴组 → {@link MessageCategoryEnum#WEAR}，进度组 → {@link MessageCategoryEnum#SYSTEM_CARE}。
     */
    public MessageCategoryEnum toCategory() {
        return isWearGroup() ? MessageCategoryEnum.WEAR : MessageCategoryEnum.SYSTEM_CARE;
    }

    /** 按编码查找枚举，未匹配返回 null */
    public static WearReminderTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (WearReminderTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 是否为佩戴组（不含 WEAR_GUARD 外的进度判断便捷方法）。 */
    public static boolean isWearGroupType(String code) {
        WearReminderTypeEnum type = getByCode(code);
        return type != null && type.isWearGroup();
    }
}
