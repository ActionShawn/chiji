package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 反馈处理状态枚举。
 * <p>
 * 预留状态机扩展：PENDING（待处理）→ PROCESSED（已处理）；
 * 后续如需「处理中」等中间态，在枚举中追加即可，前端按 {@link #getCode()} 映射徽标。
 */
public enum FeedbackStatusEnum implements IEnum<String> {

    /** 待处理：提交后默认状态 */
    PENDING("PENDING", "待处理"),
    /** 已处理：管理员已回复/已解决 */
    PROCESSED("PROCESSED", "已处理");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    FeedbackStatusEnum(String code, String desc) {
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
    public static FeedbackStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (FeedbackStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
