/**
 * 统一响应封装类
 *
 * 作用：标准化 API 响应格式，所有接口返回相同结构
 *
 * 响应格式：
 * {
 *   "code": 200,        // HTTP 状态码
 *   "data": {...},      // 业务数据
 *   "message": "请求成功"  // 提示信息
 * }
 */
package com.msz.resume.ai.auth.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统一响应结构
 * @param <T> 数据类型
 */
public record RestBean<T>(int code, T data, String message) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 成功响应（带数据） */
    public static <T> RestBean<T> success(T data) {
        return new RestBean<>(200, data, "请求成功");
    }

    /** 成功响应（无数据） */
    public static <T> RestBean<T> success() {
        return success(null);
    }

    /** 成功响应（仅消息） */
    public static <T> RestBean<T> success(String message) {
        return new RestBean<>(200, null, message);
    }

    /** 失败响应（指定状态码） */
    public static <T> RestBean<T> failure(int code, String message) {
        return new RestBean<>(code, null, message);
    }

    /** 401 未认证响应 */
    public static <T> RestBean<T> unauthorized(String message) {
        return failure(401, message);
    }

    /** 403 无权限响应 */
    public static <T> RestBean<T> forbidden(String message) {
        return failure(403, message);
    }

    /** 400 请求错误响应 */
    public static <T> RestBean<T> badRequest(String message) {
        return failure(400, message);
    }

    /** 将响应对象序列化为 JSON 字符串 */
    public String asJsonString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"code\":500,\"data\":null,\"message\":\"JSON序列化失败\"}";
        }
    }
}
