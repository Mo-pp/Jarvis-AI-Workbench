/**
 * Spring Security 核心配置类
 *
 * 作用：配置 HTTP 安全策略、过滤器链、异常处理
 *
 * 主要配置：
 * 1. URL 权限规则（哪些路径需要认证，哪些公开访问）
 * 2. 禁用不需要的功能（表单登录、CSRF、Session）
 * 3. 添加 JWT 认证过滤器
 * 4. 配置异常处理（401/403）
 */
package com.msz.resume.ai.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.auth.JwtUtils;
import com.msz.resume.ai.auth.common.RestBean;
import com.msz.resume.ai.auth.filter.JwtAuthenticationFilter;
import com.msz.resume.ai.auth.service.AccountService;
import com.msz.resume.ai.auth.vo.AuthorizeVO;
import com.msz.resume.ai.auth.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfiguration {

    private final JwtUtils jwtUtils;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public SecurityConfiguration(JwtUtils jwtUtils,
                                 JwtAuthenticationFilter jwtAuthenticationFilter,
                                 AccountService accountService,
                                 ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    /**
     * 安全过滤器链配置
     * 核心逻辑：
     * 1. 配置 URL 权限（permitAll = 公开访问，authenticated = 需要登录）
     * 2. 禁用 Session（无状态 JWT 模式）
     * 3. 添加 JWT 过滤器
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(conf -> conf
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/ask-code",
                                "/api/auth/reset-password",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/debug/**").permitAll()
                        .requestMatchers("/api/claude/**", "/api/user/**").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(conf -> {
                    conf.accessDeniedHandler(this::onAccessDenied);
                    conf.authenticationEntryPoint(this::onUnauthorized);
                })
                .sessionManagement(conf -> conf.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 获取认证管理器，用于登录时验证用户名密码 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 处理未认证异常（401）
     * 用户访问需要登录的资源但未携带有效 Token 时触发
     */
    private void onUnauthorized(HttpServletRequest request,
                                HttpServletResponse response,
                                AuthenticationException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(401);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(
                RestBean.unauthorized("未登录或登录已过期，请重新登录")));
    }

    /**
     * 处理权限不足异常（403）
     * 已登录用户访问无权限的资源时触发
     */
    private void onAccessDenied(HttpServletRequest request,
                                HttpServletResponse response,
                                AccessDeniedException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(403);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(
                RestBean.forbidden("权限不足")));
    }
}
