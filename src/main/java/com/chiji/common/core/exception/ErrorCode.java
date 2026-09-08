// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\exception\ErrorCode.java
package com.chiji.common.core.exception;

/**
 * 错误码枚举。
 * <p>
 * 0 表示成功；1xx~5xx 沿用 HTTP 语义；6xxxx 为基础设施类错误；
 * 业务错误码预留段：10000+ 认证、20000+ 计划阶段、30000+ 记录、40000+ 标签、50000+ 对比，
 * 业务模块落地时在本枚举中追加。
 */
public enum ErrorCode {

    /** 操作成功。 */
    SUCCESS(0, "操作成功"),

    /** 请求参数错误。 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未登录或登录已过期。 */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 没有操作权限。 */
    FORBIDDEN(403, "没有操作权限"),

    /** 请求的资源不存在。 */
    NOT_FOUND(404, "请求的资源不存在"),

    /** 系统内部错误。 */
    INTERNAL_ERROR(500, "系统内部错误"),

    /** 微信登录失败。 */
    WECHAT_LOGIN_FAILED(60001, "微信登录失败"),

    /** 对象存储服务异常。 */
    OSS_ERROR(60002, "对象存储服务异常"),

    /** AI 服务调用失败。 */
    AI_CALL_FAILED(60003, "AI 服务调用失败"),

    /** 文件类型不支持（仅允许图片/视频）。 */
    FILE_TYPE_NOT_SUPPORTED(60004, "仅支持图片和视频"),
    /** 文件超过大小限制。 */
    FILE_TOO_LARGE(60005, "文件太大了"),
    /** 视频时长超过 15 秒上限。 */
    VIDEO_DURATION_TOO_LONG(60006, "视频不能超过15秒"),

    // ─────────────────────── 业务错误码预留段 ───────────────────────
    // 10000+ 认证模块
    /** 用户不存在。 */
    USER_NOT_FOUND(10001, "用户不存在"),
    // 20000+ 计划阶段模块
    /** 阶段不存在。 */
    STAGE_NOT_FOUND(20001, "阶段不存在"),
    // 30000+ 记录模块
    /** 记录不存在。 */
    RECORD_NOT_FOUND(30001, "记录不存在"),
    /** 记录正文不能为空。 */
    RECORD_TEXT_REQUIRED(30002, "写点什么再保存吧"),
    /** 记录正文超出长度限制。 */
    RECORD_TEXT_TOO_LONG(30003, "正文最多300字"),
    /** 标签数量超出限制。 */
    RECORD_TAGS_TOO_MANY(30004, "最多贴6个标签"),
    // 40000+ 标签模块（预留，未使用）
    // 50000+ 对比模块（预留，未使用）
    // 60000+ 基础设施
    // 70000+ 佩戴时长模块
    /** 没有可撤销的佩戴中会话。 */
    WEAR_ACTIVE_SESSION_NOT_FOUND(70001, "没有可撤销的佩戴记录"),
    /** 补录日期超出允许范围（仅支持最近 7 天内）。 */
    WEAR_MAKEUP_OUT_OF_RANGE(70002, "只能补录最近7天的佩戴"),
    /** 补录时间段不合法（时间跨日 / 起止倒挂 / 超过 24h）。 */
    WEAR_MAKEUP_TIME_INVALID(70003, "补录时间段不合法"),
    /** 补录时间段与已有佩戴记录重叠。 */
    WEAR_MAKEUP_OVERLAP(70004, "与已有佩戴时间段重叠"),
    /** 每日目标超出允许范围（18.0–22.0h）。 */
    WEAR_GOAL_OUT_OF_RANGE(70005, "目标需在18.0–22.0小时之间"),
    /** 佩戴记录不存在。 */
    WEAR_SESSION_NOT_FOUND(70006, "佩戴记录不存在"),
    /** 牙套副不存在。 */
    WEAR_ALIGNER_NOT_FOUND(70007, "牙套副不存在"),
    /** 佩戴参数不合法（时间格式/动作取值/补录时段等）。 */
    WEAR_PARAM_INVALID(70008, "佩戴参数不合法"),
    // 80000+ 消息/通知模块
    /** 消息不存在。 */
    MESSAGE_NOT_FOUND(80001, "消息不存在"),
    /** 通知设置参数不合法。 */
    NOTIFICATION_PARAM_INVALID(80002, "通知设置参数不合法");
    // ──────────────────────────────────────────────────────────────

    /** 错误码。 */
    private final int code;

    /** 默认提示语。 */
    private final String message;

    /**
     * 构造方法。
     *
     * @param code    错误码
     * @param message 默认提示语
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认提示语。
     *
     * @return 提示语
     */
    public String getMessage() {
        return message;
    }
}
