package com.chiji.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注销原因码白名单枚举。
 * <p>
 * 仅用于「注销原因」入参的存量校验（码本身无业务含义，中文文案由前端维护）：
 * 后端按本枚举过滤，未知码<b>丢弃不报错</b>——既不落脏数据，也不因前端版本差异导致注销失败。
 * 取值与方案 §4.3 严格对齐。
 */
public enum UserCancelReasonEnum {

    /** 矫正已经结束，不再需要 */
    TREATMENT_ENDED,
    /** 没时间记录，用不上 */
    NO_TIME,
    /** 功能太复杂，用不明白 */
    TOO_COMPLEX,
    /** 提醒太频繁，有点打扰 */
    TOO_MANY_REMARKS,
    /** 记录的数据不准 */
    DATA_INACCURATE,
    /** 有 bug 或太卡 */
    BUG_OR_PERF,
    /** 担心隐私安全 */
    PRIVACY_CONCERN,
    /** 其他原因（可补充说明） */
    OTHER;

    /** 全量合法码集合（不可变） */
    public static final Set<String> CODES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 是否为合法原因码。
     *
     * @param code 待校验码（可空）
     * @return true=白名单内
     */
    public static boolean isValidCode(String code) {
        return code != null && CODES.contains(code);
    }
}
