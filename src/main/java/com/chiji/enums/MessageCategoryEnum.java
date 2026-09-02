// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\MessageCategoryEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 消息分类枚举。
 * <p>
 * 对应 {@code tagStyle} 的四种消息分类；{@code code} 与枚举 {@code name()} 保持一致。
 * 注意：FELLOW_TRAVELER（同路人）仅是消息分类标识，本后端不承载任何社区/社交功能。
 */
public enum MessageCategoryEnum implements IEnum<String> {

    /** 记录提醒 */
    RECORD_REMINDER("RECORD_REMINDER", "记录提醒"),
    /** 阶段更新 */
    STAGE_UPDATE("STAGE_UPDATE", "阶段更新"),
    /** 同路人（仅消息分类标识，无社交后端） */
    FELLOW_TRAVELER("FELLOW_TRAVELER", "同路人"),
    /** 系统关怀 */
    SYSTEM_CARE("SYSTEM_CARE", "系统关怀");

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
