/**
 * 修改密码请求 VO
 *
 * 作用：封装修改密码请求参数
 *
 * 使用场景：已登录用户修改密码（需要验证旧密码）
 *
 * 请求示例：
 * {
 *   "oldPassword": "oldpass",
 *   "newPassword": "newpass"
 * }
 */
package com.msz.resume.ai.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordVO {

    /** 旧密码（需验证正确性） */
    @NotBlank(message = "旧密码不能为空")
    @Size(min = 6, max = 100, message = "旧密码长度 6-100 字符")
    private String oldPassword;

    /** 新密码（不能与旧密码相同） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度 6-100 字符")
    private String newPassword;
}
