// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\exception\GlobalExceptionHandler.java
package com.chiji.common.core.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.chiji.common.core.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 按优先级依次处理：业务异常 → Sa-Token 未登录(401)/无权限(403) →
 * 参数校验异常(400，拼接字段错误) → 上传超限 → 兜底未知异常(500，打印带 traceId 的错误日志)。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常。
     *
     * @param e 业务异常
     * @return 携带业务错误码与提示的响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 未登录（Sa-Token 抛出）。
     *
     * @param e 未登录异常
     * @return 401 响应
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e) {
        return R.fail(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 无权限（Sa-Token 抛出）。
     *
     * @param e 无权限异常
     * @return 403 响应
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e) {
        return R.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * {@code @RequestBody} 参数校验失败，仅取校验注解上的中文 message。
     * <p>
     * 不再拼接英文字段名（如 {@code name}），直接展示 {@code @NotBlank(message=...)} 等注解配置的中文提示，
     * 多个错误用分号连接，保证用户可见的提示语自然可读。
     *
     * @param e 参数校验异常
     * @return 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 表单绑定参数校验失败，仅取校验注解上的中文 message（同上，不拼字段名）。
     *
     * @param e 绑定异常
     * @return 400 响应
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        String message = errors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 方法级参数校验失败（{@code @Validated} 标注的 Controller 方法参数）。
     *
     * @param e 校验异常
     * @return 400 响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 请求体不可读（如 JSON 解析失败、缺失请求体）。
     *
     * @param e 可读性异常
     * @return 400 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ErrorCode.BAD_REQUEST);
    }

    /**
     * 文件上传超过大小限制。
     *
     * @param e 上传超限异常
     * @return 400 响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), "上传文件过大，请控制在允许范围内");
    }

    /**
     * 路径/静态资源不存在（Spring 6.1+ 抛出）。
     * <p>
     * 属正常请求语义，不记 ERROR 日志（避免云托管健康探针等未匹配路径误报「系统异常」），直接返回 404。
     *
     * @param e 资源不存在异常
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        return R.fail(ErrorCode.NOT_FOUND);
    }

    /**
     * 兜底异常：打印带 traceId 的错误日志后返回内部错误。
     *
     * @param e 未知异常
     * @return 500 响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常, traceId={}", MDC.get("traceId"), e);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }
}
