package com.chiji.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 反馈处理状态枚举（三态：连续对话状态机，2026-10-08 对话化改造）。
 * <p>
 * 流转规则（闭环权在用户）：
 * <ul>
 *   <li>提交 → {@link #WAIT_ADMIN}（等运营回复）</li>
 *   <li>运营发评论 → {@link #WAIT_USER}（待用户确认）</li>
 *   <li>用户发评论 → {@link #WAIT_ADMIN}（等运营回复；CLOSED 时为「追问」自动复活）</li>
 *   <li>用户点「已解决」→ {@link #CLOSED}（5 秒内可撤回回 {@link #WAIT_USER}）</li>
 *   <li>运营不可单方关闭/重开（接口已禁用）</li>
 * </ul>
 */
public enum FeedbackStatusEnum implements IEnum<String> {

    /** 等回复：已提交，等待运营回复 */
    WAIT_ADMIN("WAIT_ADMIN", "等待回复"),
    /** 待你确认：运营已回复，等待用户确认闭环或追问 */
    WAIT_USER("WAIT_USER", "待你确认"),
    /** 已闭环：用户点「已解决」（或存量数据迁移映射），可追问复活 */
    CLOSED("CLOSED", "已闭环");

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
