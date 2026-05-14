/**
 * JWT 认证过滤器
 *
 * 作用：拦截每个请求，验证 JWT Token 并设置 Spring Security 认证信息
 *
 * 执行流程：
 * 1. 从请求头提取 Authorization: Bearer xxx
 * 2. 调用 JwtUtils 验证 Token（签名、过期、黑名单）
 * 3. 验证通过 → 构建 Authentication 对象 → 存入 SecurityContext
 * 4. 将 userId 存入 request.setAttribute，供后续 Controller 使用
 *
 * 继承 OncePerRequestFilter 确保每次请求只过滤一次
 */
package com.msz.resume.ai.auth.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * 过滤器核心逻辑
     * 验证 Token → 设置 SecurityContext → 存入 userId
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从请求头提取 Authorization
        String authorization = request.getHeader("Authorization");

        // 2. 验证并解析 Token
        DecodedJWT jwt = jwtUtils.resolveToken(authorization);

        if (jwt != null) {
            // 3. Token 有效，提取用户信息
            UserDetails user = jwtUtils.toUser(jwt);

            // 4. 创建 Spring Security 认证对象
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 5. 设置到 SecurityContext（让 Spring Security 知道当前用户是谁）
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 6. 将用户ID存入请求属性，方便 Controller 获取
            request.setAttribute(Const.ATTR_USER_ID, jwtUtils.toUserId(jwt));
        }
        // Token 无效或不存在时不设置认证信息，后续 SecurityConfiguration 会处理

        // 7. 继续过滤器链
        filterChain.doFilter(request, response);
    }
}
