package com.chiji.enums;

/**
 * 牙套膜片类型枚举（仅双模模式使用）。
 * <p>
 * 双模模式下每个序号对应两副实体：软膜（SOFT，牙齿初步移动，异物感小）
 * 与硬膜（HARD，同序号，力量更强，推动牙齿到位），
 * aligner.film_type 落 name()；单模阶段该字段为 null。
 */
public enum AlignerFilmEnum {

    /** 软膜：每组前段佩戴，牙齿初步移动，异物感小 */
    SOFT("SOFT", "软膜"),
    /** 硬膜：同序号软膜戴满后更换，力量更强，强力推动牙齿到位 */
    HARD("HARD", "硬膜");

    /** 编码（落库 name()） */
    private final String code;

    /** 中文名称（节点标签用） */
    private final String desc;

    AlignerFilmEnum(String code, String desc) {
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
    public static AlignerFilmEnum getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (AlignerFilmEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
