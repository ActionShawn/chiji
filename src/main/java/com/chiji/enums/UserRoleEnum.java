package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 用户角色枚举。
 * <p>
 * 管理员通过 SQL 手动提权（无接口入口），拥有管理后台功能权限（/api/admin/**）。
 */
public enum UserRoleEnum implements IEnum<String> {

    /** 普通用户（默认） */
    USER("USER", "普通用户"),
    /** 管理员 */
    ADMIN("ADMIN", "管理员");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    UserRoleEnum(String code, String desc) {
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
    public static UserRoleEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserRoleEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
