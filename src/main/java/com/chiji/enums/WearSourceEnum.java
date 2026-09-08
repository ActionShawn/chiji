package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 佩戴会话来源枚举。
 * <p>
 * 对应 {@code wear_session.source}；{@code code} 与枚举 {@code name()} 保持一致。
 * 手工打卡与「晨起补记」都属 MANUAL（真实佩戴，跨午夜不预拆）；MAKEUP 仅用于
 * 「校准/补录忘记打卡的时间段」，补录段落库时带上 {@code makeup_for} 目标自然日，
 * 展示上与正常打卡分开统计。
 */
public enum WearSourceEnum implements IEnum<String> {

    /** 手工打卡 / 晨起补记（真实佩戴） */
    MANUAL("MANUAL", "手工打卡"),
    /** 校准补录（忘记打卡的时间段） */
    MAKEUP("MAKEUP", "校准补录");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    WearSourceEnum(String code, String desc) {
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
    public static WearSourceEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (WearSourceEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
