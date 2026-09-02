package com.chiji.enums;

/**
 * 治疗方式枚举。
 * <p>
 * 齿迹面向所有牙齿矫正用户，目前支持三种治疗方式：
 * <ul>
 *   <li>{@link #CLEAR_ALIGNER} 隐形矫正：隐适美 / 时代天使 / 正雅（已开发）</li>
 *   <li>{@link #FIXED_BRACKET} 固定托槽矫正：传统金属 / 陶瓷 / 自锁（暂未开发）</li>
 *   <li>{@link #LINGUAL} 舌侧矫正（暂未开发）</li>
 * </ul>
 *
 * <p>扩展性：未来新增治疗方式只需在本枚举追加一项，并将 {@code available} 置为 true；
 * 前端通过 {@code available} 字段决定卡片是否可选，未开放的治疗方式显示「敬请期待」角标。
 *
 * <p>{@code code} 与枚举 {@code name()} 保持一致，存储于 {@code user.treatment_type} 字段。
 */
public enum TreatmentTypeEnum {

    /** 隐形矫正：隐适美 / 时代天使 / 正雅（已开发，首页走 stage + aligner 节点轴） */
    CLEAR_ALIGNER("CLEAR_ALIGNER", "隐形矫正", "隐适美 / 时代天使 / 正雅", true),

    /** 固定托槽矫正：传统金属 / 陶瓷 / 自锁（暂未开发） */
    FIXED_BRACKET("FIXED_BRACKET", "固定托槽矫正", "传统金属 / 陶瓷 / 自锁", false),

    /** 舌侧矫正（暂未开发） */
    LINGUAL("LINGUAL", "舌侧矫正", "舌侧隐形矫治器", false);

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文名称 */
    private final String name;

    /** 子类型描述（展示用） */
    private final String subTypes;

    /** 是否已开放（false 表示功能开发中，前端显示「敬请期待」角标） */
    private final boolean available;

    TreatmentTypeEnum(String code, String name, String subTypes, boolean available) {
        this.code = code;
        this.name = name;
        this.subTypes = subTypes;
        this.available = available;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSubTypes() {
        return subTypes;
    }

    public boolean isAvailable() {
        return available;
    }

    /** 按编码查找枚举，未匹配返回 null */
    public static TreatmentTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (TreatmentTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
