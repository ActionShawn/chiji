package com.chiji.enums;

/**
 * 记录模式枚举（模式 = 一等实体，阶段建时固化所属模式）。
 * <p>
 * 模式与阶段是一对多关系：1 记录模式 → N 阶段，不同模式的阶段与数据彼此独立，
 * 首页按当前模式隔离展示（模式即工作台）。
 * 未来新增治疗模式（如金属托槽 FIXED_BRACKET）只需追加枚举项，
 * 与前端 {@code utils/treatment.js} 的模式注册表对齐。
 */
public enum RecordModeEnum {

    /** 隐形单模版：每副佩戴周期固定，到期直接换下一副 */
    CLEAR_SINGLE("CLEAR_SINGLE", "隐形单模版"),
    /** 隐形双模板：每组先戴软膜（初步移动）再换同序号硬膜（强力推动），软硬天数可分别设置 */
    CLEAR_DUAL("CLEAR_DUAL", "隐形双模板");

    /** 模式编码（落库到 stage.mode，与枚举 name() 一致） */
    private final String code;

    /** 中文名称 */
    private final String desc;

    RecordModeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 按编码查找枚举，未匹配返回 null */
    public static RecordModeEnum getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (RecordModeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 是否双模模式 */
    public boolean isDual() {
        return this == CLEAR_DUAL;
    }

    /** 判断指定编码是否双模（null/未知编码按单模处理，兼容存量数据） */
    public static boolean isDualCode(String code) {
        RecordModeEnum mode = getByCode(code);
        return mode != null && mode.isDual();
    }
}
