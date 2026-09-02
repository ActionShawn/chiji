package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 反馈联系方式类型枚举。
 * <p>
 * 联系方式选填，可留空；填写时二选一：手机号 / 邮箱。
 */
public enum FeedbackContactTypeEnum implements IEnum<String> {

    /** 手机号（大陆 11 位） */
    PHONE("PHONE", "手机号"),
    /** 邮箱 */
    EMAIL("EMAIL", "邮箱");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    FeedbackContactTypeEnum(String code, String desc) {
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
}
