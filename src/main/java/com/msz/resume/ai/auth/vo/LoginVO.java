/**
 * 登录请求 VO
 *
 * 作用：封装登录请求参数
 *
 * 请求示例：
 * {
 *   "username": "admin",    // 用户名或邮箱
 *   "password": "123456",
 *   "remember": true        // 可选，延长 Token 有效期至 30 天
 * }
 */
package com.msz.resume.ai.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginVO {

    /** 用户名（支持用户名或邮箱登录） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度 2-50 字符")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100 字符")
    private String password;

    /** 记住我：true 时 Token 有效期延长至 30 天 */
    private Boolean remember = false;
}
