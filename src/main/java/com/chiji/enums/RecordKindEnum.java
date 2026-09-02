// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\enums\RecordKindEnum.java
package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 记录内容类型枚举（整条记录按媒体汇总）。
 * <p>
 * 对应 {@code timelineRecords} 的 mediaKind；保存记录时按媒体列表计算
 * （无媒体=TEXT、全图=IMAGE、全视频=VIDEO、混合=MIXED）。{@code code} 与枚举 {@code name()} 保持一致。
 */
public enum RecordKindEnum implements IEnum<String> {

    /** 纯文字 */
    TEXT("TEXT", "纯文字"),
    /** 图片 */
    IMAGE("IMAGE", "图片"),
    /** 视频 */
    VIDEO("VIDEO", "视频"),
    /** 图文视频混合 */
    MIXED("MIXED", "图文视频混合");

    /** 编码（与枚举 name() 一致） */
    private final String code;

    /** 中文描述 */
    private final String desc;

    RecordKindEnum(String code, String desc) {
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
    public static RecordKindEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (RecordKindEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
