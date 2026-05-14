/**
 * 登录成功响应 VO
 *
 * 作用：封装登录成功后返回给客户端的数据
 *
 * 响应示例：
 * {
 *   "id": 1,
 *   "username": "admin",
 *   "email": "admin@example.com",
 *   "token": "eyJhbGciOiJIUzI1NiIs...",
 *   "expire": "2024-05-08T12:00:00.000+00:00"
 * }
 */
package com.msz.resume.ai.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeVO {

    /** 用户ID */
    private Integer id;

    /** 用户名 */
    private String username;

    /** 邮箱 */
    private String email;

    /** JWT Token（客户端需保存，后续请求携带） */
    private String token;

    /** Token 过期时间 */
    private Date expire;
}
