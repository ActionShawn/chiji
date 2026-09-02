// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\result\R.java
package com.chiji.common.core.result;

import com.chiji.common.core.exception.ErrorCode;

/**
 * 统一响应体。
 * <p>
 * 使用 record 表达不可变的响应结构，配合静态工厂方法 {@link #ok()} / {@link #fail(ErrorCode)}
 * 快速构造，所有接口统一返回本类型。
 *
 * @param code    业务错误码（0 表示成功）
 * @param message 提示信息
 * @param data    业务数据
 * @param <T>     业务数据类型
 */
public record R<T>(Integer code, String message, T data) {

    /**
     * 成功响应（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应体
     */
    public static <T> R<T> ok() {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（携带数据）。
     * <p>
     * 提示语取 {@link ErrorCode#SUCCESS} 的默认值「操作成功」。前端对默认成功提示不弹 toast，
     * 因此适用于查询类接口（无需成功反馈）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应体
     */
    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（携带数据 + 自定义提示）。
     * <p>
     * 用于写操作（创建 / 更新 / 删除）需向用户反馈结果的场景。前端检测到 message 非默认「操作成功」时，
     * 会自动弹出成功 toast，无需页面手动调用 wx.showToast。
     *
     * @param data    业务数据
     * @param message 自定义成功提示
     * @param <T>     数据类型
     * @return 成功响应体
     */
    public static <T> R<T> ok(T data, String message) {
        return new R<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应（基于错误码枚举）。
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型
     * @return 失败响应体
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败响应（自定义错误码与提示）。
     *
     * @param code    错误码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 失败响应体
     */
    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message, null);
    }
}
