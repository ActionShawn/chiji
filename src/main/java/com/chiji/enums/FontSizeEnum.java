// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\FontSizeEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 字体大小枚举。
 * <p>
 * 实现 {@link IEnum} 供 MyBatis-Plus 序列化：{@code code} 与枚举 {@code name()} 保持一致，
 * 故实体字段按 String 存 name() 与 IEnum 的 getValue() 语义互通。
 */
public enum FontSizeEnum implements IEnum<String> {

    /** 小 */
    SMALL("SMALL", "小"),
    /** 中 */
    MEDIUM("MEDIUM", "中"),
    /** 大 */
    LARGE("LARGE", "大");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    FontSizeEnum(String code, String desc) {
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
    public static FontSizeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (FontSizeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
