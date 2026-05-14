/**
 * 重置密码请求 VO
 *
 * 作用：封装重置密码请求参数
 *
 * 使用场景：用户忘记密码，通过邮箱验证码重置
 *
 * 请求示例：
 * {
 *   "email": "user@example.com",
 *   "code": "123456",
 *   "password": "newpassword"
 * }
 */
package com.msz.resume.ai.auth.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordVO {

    /** 邮箱地址 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(min = 4, max = 100, message = "邮箱长度 4-100 字符")
    private String email;

    /** 邮箱验证码（需先调用 /ask-code?type=reset） */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码必须是 6 位")
    private String code;

    /** 新密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100 字符")
    private String password;
}
