// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\StageStatusEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 阶段状态枚举。
 * <p>
 * 阶段之间没有严格的前后关系，仅标记启用 / 结束两种状态：
 * 同一时间至多一个 {@link #ACTIVE} 阶段，新建阶段时会自动把旧的启用阶段置为 {@link #ENDED}。
 * 用户可在下拉框中任意切换查看不同阶段（含已结束阶段）的牙套节点。
 *
 * <p>对应前端 {@code spineColor} / {@code statusChip} 的阶段徽标；
 * {@code code} 与枚举 {@code name()} 保持一致，
 * 实体按 String 存 name() 与 IEnum 的 getValue() 语义互通。
 */
public enum StageStatusEnum implements IEnum<String> {

    /** 启用：进行中的阶段，同一时间至多一个 */
    ACTIVE("ACTIVE", "启用"),
    /** 结束：已走完的阶段，仍可在下拉框切换查看其牙套节点 */
    ENDED("ENDED", "结束");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    StageStatusEnum(String code, String desc) {
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
    public static StageStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (StageStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
