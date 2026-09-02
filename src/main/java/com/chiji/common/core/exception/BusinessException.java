// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\exception\BusinessException.java
package com.chiji.common.core.exception;

/**
 * 业务异常。
 * <p>
 * 业务代码中用于主动抛出可预期的失败，携带错误码与提示信息，
 * 由 {@link com.chiji.common.core.exception.GlobalExceptionHandler} 统一转换为 {@link com.chiji.common.core.result.R} 响应。
 */
public class BusinessException extends RuntimeException {

    /** 错误码。 */
    private final int code;

    /**
     * 使用错误码枚举构造（提示语取枚举默认值）。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码枚举 + 自定义提示构造。
     *
     * @param errorCode 错误码枚举
     * @param message   自定义提示
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}
