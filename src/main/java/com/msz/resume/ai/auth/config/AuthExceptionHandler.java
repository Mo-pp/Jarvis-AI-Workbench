/**
 * 认证模块全局异常处理器
 *
 * 作用：统一处理认证模块的参数校验异常，返回友好的错误信息
 *
 * 处理的异常类型：
 * - MethodArgumentNotValidException: @RequestBody 参数校验失败
 * - BindException: 表单参数绑定失败
 * - ConstraintViolationException: 单个参数校验失败
 * - HttpMessageNotReadableException: 请求体格式错误
 */
package com.msz.resume.ai.auth.config;

import com.msz.resume.ai.auth.common.RestBean;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.msz.resume.ai.auth")
public class AuthExceptionHandler {

    /** 处理 @RequestBody 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestBean<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return RestBean.badRequest(formatFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    /** 处理表单参数绑定异常 */
    @ExceptionHandler(BindException.class)
    public RestBean<Void> handleBind(BindException exception) {
        return RestBean.badRequest(formatFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    /** 处理单个参数校验异常（如 @RequestParam 上的校验） */
    @ExceptionHandler(ConstraintViolationException.class)
    public RestBean<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("; "));
        return RestBean.badRequest(message.isBlank() ? "请求参数不合法" : message);
    }

    /** 处理请求体格式错误（如 JSON 格式不正确） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public RestBean<Void> handleUnreadableBody() {
        return RestBean.badRequest("请求体格式不正确");
    }

    /** 格式化字段错误信息，合并多条错误 */
    private String formatFieldErrors(List<FieldError> errors) {
        String message = errors.stream()
                .map(error -> error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : error.getField() + " 参数不合法")
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
        return message.isBlank() ? "请求参数不合法" : message;
    }
}
