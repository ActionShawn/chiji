// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\MediaKindEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 媒体类型枚举（单条媒体）。
 * <p>
 * 对应 {@code record_media.type}；前端 media 数组的 kind 为 image/video（
 * 记录弹层 tiles 用 photo/video，二者归一到本枚举）。{@code code} 与枚举 {@code name()} 保持一致。
 */
public enum MediaKindEnum implements IEnum<String> {

    /** 图片 */
    IMAGE("IMAGE", "图片"),
    /** 视频 */
    VIDEO("VIDEO", "视频");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    MediaKindEnum(String code, String desc) {
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
    public static MediaKindEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (MediaKindEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
