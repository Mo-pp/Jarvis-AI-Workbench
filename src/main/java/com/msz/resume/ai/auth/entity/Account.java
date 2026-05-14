/**
 * 用户账户实体类
 *
 * 作用：映射数据库表 db_account，存储用户基本信息
 *
 * 字段说明：
 * - id: 主键，自增
 * - username: 用户名（登录名）
 * - password: 密码（BCrypt 加密）
 * - email: 邮箱（用于验证码登录/重置密码）
 * - avatar: 头像 URL
 * - registerTime: 注册时间
 */
package com.msz.resume.ai.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("db_account")
public class Account {

    /** 用户ID，主键自增 */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 加密存储） */
    private String password;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** OpenViking 为该用户创建的 admin key */
    private String openvikingAdminKey;
}
