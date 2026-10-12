package com.chiji.module.chew.support;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 咬胶会话来源枚举。
 * <p>
 * 对应 {@code chew_session.source}；{@code code} 与枚举 {@code name()} 保持一致。
 * REALTIME 为本期唯一使用来源（实时计时）；MANUAL（手动补录）为预留。
 */
public enum ChewSourceEnum implements IEnum<String> {

    /** 实时计时（本期唯一使用来源） */
    REALTIME("REALTIME", "实时计时"),
    /** 手动补录（预留，本期不产生） */
    MANUAL("MANUAL", "手动补录");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    ChewSourceEnum(String code, String desc) {
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
    public static ChewSourceEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ChewSourceEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
