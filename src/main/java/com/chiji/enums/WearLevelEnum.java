package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 每日佩戴达标等级枚举。
 * <p>
 * 对应 {@code wear_daily_summary.level}；{@code code} 与枚举 {@code name()} 保持一致。
 * 以当日实际佩戴（目标快照）判定：FULL ≥ 目标；PARTIAL ≥ 目标 - 4h；NONE &lt; 目标 - 4h。
 * 前端据此着色（绿 / 沙 / 红）。
 */
public enum WearLevelEnum implements IEnum<String> {

    /** 达标（≥ 目标） */
    FULL("FULL", "达标"),
    /** 部分达标（≥ 目标 - 4h） */
    PARTIAL("PARTIAL", "部分达标"),
    /** 未达标（&lt; 目标 - 4h） */
    NONE("NONE", "未达标");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    WearLevelEnum(String code, String desc) {
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
    public static WearLevelEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (WearLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
