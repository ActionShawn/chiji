package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 复诊档案状态枚举。
 * <p>
 * {@code clinic_visit.status} 存 {@code name()}：同表承载「已确认日程(PLANNED)」
 * 与「就诊记录(DONE)」，完成动作即 PLANNED → DONE 的状态迁移。
 */
public enum ClinicVisitStatusEnum implements IEnum<String> {

    /** 待复诊（已确认日程） */
    PLANNED("PLANNED"),
    /** 已完成（就诊记录） */
    DONE("DONE");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    ClinicVisitStatusEnum(String code) {
        this.code = code;
    }

    @Override
    public String getValue() {
        return code;
    }

    public String getCode() {
        return code;
    }

    /** 按编码查找枚举，未匹配返回 null */
    public static ClinicVisitStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ClinicVisitStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
