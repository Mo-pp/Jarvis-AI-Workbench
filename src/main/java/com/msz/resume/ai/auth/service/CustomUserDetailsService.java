/**
 * Spring Security 用户详情服务实现
 *
 * 作用：为 Spring Security 提供用户认证信息
 *
 * 调用时机：
 * - 用户登录时，AuthenticationManager 调用此服务加载用户信息
 * - 返回的 UserDetails 用于密码比对
 *
 * 流程：
 * 1. AuthenticationManager 接收用户名密码
 * 2. 调用 loadUserByUsername() 查询数据库
 * 3. 返回 UserDetails（包含加密后的密码）
 * 4. Spring Security 自动比对密码
 */
package com.msz.resume.ai.auth.service;

import com.msz.resume.ai.auth.entity.Account;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountService accountService;

    public CustomUserDetailsService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 根据用户名加载用户信息
     * 支持用户名或邮箱登录
     * @return UserDetails 包含用户名、加密密码、权限
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 支持用户名或邮箱登录
        Account account = accountService.findByUsernameOrEmail(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 构建 Spring Security 的 UserDetails
        return User.builder()
                .username(account.getUsername())
                .password(account.getPassword())  // BCrypt 加密后的密码
                .roles("USER")                    // 默认角色
                .build();
    }
}
