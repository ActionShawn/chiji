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
    RECORD_TAGS_TOO_MANY(30004, "最多贴6个标签");
    // 40000+ 标签模块
    // 50000+ 对比模块
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
