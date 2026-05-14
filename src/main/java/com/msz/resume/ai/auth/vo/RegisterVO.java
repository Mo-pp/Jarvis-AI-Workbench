/**
 * 注册请求 VO
 *
 * 作用：封装注册请求参数
 *
 * 请求示例：
 * {
 *   "email": "user@example.com",
 *   "code": "123456",        // 邮箱验证码
 *   "username": "admin",
 *   "password": "123456"
 * }
 */
package com.msz.resume.ai.auth.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterVO {

    /** 邮箱地址 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(min = 4, max = 100, message = "邮箱长度 4-100 字符")
    private String email;

    /** 邮箱验证码（6位数字） */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码必须是 6 位")
    private String code;

    /** 用户名（不支持中文，限制为 OpenViking 可接受字符） */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.+-]+$", message = "用户名暂不支持中文名，且只能包含字母、数字、点、下划线、短横线、加号")
    @Size(min = 2, max = 50, message = "用户名长度 2-50 字符")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100 字符")
    private String password;
}
