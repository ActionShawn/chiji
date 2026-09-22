// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\MessageCategoryEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 消息分类枚举。
 * <p>
 * 对应信箱消息的 chip 分类（前端 {@code mock.tagStyle} 按编码映射中文与配色）：
 * 佩戴提醒（沙）/ 佩戴进度（绿）/ 复诊提醒（紫）/ 系统关怀（蓝）。{@code code} 与枚举
 * {@code name()} 保持一致。
 * 注意：
 * <ul>
 *   <li>FELLOW_TRAVELER（同路人）仅是消息分类标识，本后端不承载任何社区/社交功能</li>
 *   <li>提醒类消息按 {@link com.chiji.enums.WearReminderTypeEnum} 声明的分类落库：
 *       佩戴打卡组 → {@link #WEAR}，换副/记录/里程碑 → {@link #PROGRESS}，
 *       复诊两类 → {@link #CLINIC}，问候 → {@link #SYSTEM_CARE}</li>
 *   <li>RECORD_REMINDER / STAGE_UPDATE / FELLOW_TRAVELER 为存量分类，保留兼容</li>
 * </ul>
 */
public enum MessageCategoryEnum implements IEnum<String> {

    /** 记录提醒 */
    RECORD_REMINDER("RECORD_REMINDER", "记录提醒"),
    /** 阶段更新 */
    STAGE_UPDATE("STAGE_UPDATE", "阶段更新"),
    /** 同路人（仅消息分类标识，无社交后端） */
    FELLOW_TRAVELER("FELLOW_TRAVELER", "同路人"),
    /** 系统关怀（问候类提醒落此分类） */
    SYSTEM_CARE("SYSTEM_CARE", "系统关怀"),
    /** 佩戴提醒（佩戴打卡组提醒落此分类） */
    WEAR("WEAR", "佩戴提醒"),
    /** 佩戴进度（换副 / 每日记录 / 里程碑提醒落此分类） */
    PROGRESS("PROGRESS", "佩戴进度"),
    /** 复诊提醒（就诊提醒 / 预约提醒落此分类） */
    CLINIC("CLINIC", "复诊提醒");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    MessageCategoryEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
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

    /** 按编码查找枚举，未匹配返回 null */
    public static MessageCategoryEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (MessageCategoryEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
