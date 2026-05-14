/**
 * 密码编码器配置
 *
 * 作用：配置 BCrypt 密码加密算法
 *
 * 使用场景：
 * - 用户注册时加密密码
 * - 用户登录时验证密码
 * - 重置密码时加密新密码
 */
package com.msz.resume.ai.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfiguration {

    /** 创建 BCrypt 密码编码器，用于密码加密和验证 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
