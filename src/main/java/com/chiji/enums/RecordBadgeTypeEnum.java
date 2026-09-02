// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\RecordBadgeTypeEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 记录特殊徽标类型枚举。
 * <p>
 * 对应 {@code timelineRecords} 的 badgeSpecial 特殊徽标（区别于佩戴时长「22h」）；
 * {@code code} 与枚举 {@code name()} 保持一致。
 */
public enum RecordBadgeTypeEnum implements IEnum<String> {

    /** 复诊打卡 */
    FOLLOW_UP("FOLLOW_UP", "复诊打卡"),
    /** 里程碑 */
    MILESTONE("MILESTONE", "里程碑"),
    /** 阶段切换 */
    STAGE_SWITCH("STAGE_SWITCH", "阶段切换");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    RecordBadgeTypeEnum(String code, String desc) {
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
    public static RecordBadgeTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (RecordBadgeTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
