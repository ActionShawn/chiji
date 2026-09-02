// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\AlignerStateEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 牙套副佩戴状态枚举。
 * <p>
 * 对应 {@code alignerNodes} 的 state；{@code code} 与枚举 {@code name()} 保持一致，
 * 实体按 String 存 name() 与 IEnum 的 getValue() 语义互通。
 */
public enum AlignerStateEnum implements IEnum<String> {

    /** 已完成 */
    DONE("DONE", "已完成"),
    /** 佩戴中 */
    ACTIVE("ACTIVE", "佩戴中"),
    /** 未开始 */
    FUTURE("FUTURE", "未开始");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    AlignerStateEnum(String code, String desc) {
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
    public static AlignerStateEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlignerStateEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
