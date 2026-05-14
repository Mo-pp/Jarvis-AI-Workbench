package com.msz.resume.ai.resume.api;

import com.msz.resume.ai.shared.response.Result;
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

@RestControllerAdvice(assignableTypes = ResumeExportController.class)
public class ResumeExportExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return Result.error(formatFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException exception) {
        return Result.error(formatFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("; "));
        return Result.error(message.isBlank() ? "请求参数不合法" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody() {
        return Result.error("请求体格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalState(IllegalStateException exception) {
        return Result.error(exception.getMessage());
    }

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
