package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 通知预设模式枚举。
 * <p>
 * 对应 {@code user_setting.preset_mode}；{@code code} 与枚举 {@code name()} 保持一致。
 * 预设是一组「每提醒类型开关」的一键模板：
 * <ul>
 *   <li>STANDARD（标准）：佩戴组 4 类 + 进度组（换副/每日记录/里程碑）默认开启，仅「问候」关闭</li>
 *   <li>IMPORTANT_ONLY（仅重要）：仅保留换副提醒 + 佩戴结算结果两类</li>
 *   <li>CUSTOM（自定义）：用户在预设基础上手工拨动任一开关后即为自定义</li>
 * </ul>
 * 关闭预设会改写明细开关；之后再手工拨动会退化为 CUSTOM。
 */
public enum NotificationPresetEnum implements IEnum<String> {

    /** 标准 */
    STANDARD("STANDARD", "标准"),
    /** 仅重要 */
    IMPORTANT_ONLY("IMPORTANT_ONLY", "仅重要"),
    /** 自定义（手工调整） */
    CUSTOM("CUSTOM", "自定义");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    NotificationPresetEnum(String code, String desc) {
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
    public static NotificationPresetEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (NotificationPresetEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
