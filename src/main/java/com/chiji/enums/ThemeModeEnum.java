// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\ThemeModeEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 主题模式枚举。
 * <p>
 * 实现 {@link IEnum} 供 MyBatis-Plus 序列化：{@code code} 与枚举 {@code name()} 保持一致，
 * 故实体字段按 String 存 name() 与 IEnum 的 getValue() 语义互通。
 */
public enum ThemeModeEnum implements IEnum<String> {

    /** 浅色 */
    LIGHT("LIGHT", "浅色"),
    /** 深色 */
    DARK("DARK", "深色"),
    /** 跟随系统 */
    AUTO("AUTO", "跟随系统");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    ThemeModeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * MyBatis-Plus 序列化取值。
     *
     * @return 编码
     */
    @Override
    public String getValue() {
        return code;
    }

    /**
     * 获取编码。
     *
     * @return 编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文描述。
     *
     * @return 中文描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 按编码查找枚举，未匹配返回 null。
     *
     * @param code 编码
     * @return 对应枚举，找不到为 null
     */
    public static ThemeModeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ThemeModeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
