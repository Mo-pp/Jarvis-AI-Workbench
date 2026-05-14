/**
 * 认证控制器
 *
 * 作用：提供用户认证相关的 REST API
 *
 * 接口列表：
 * - POST /register: 用户注册
 * - POST /login: 用户登录，返回 JWT Token
 * - POST /logout: 退出登录，将 Token 加入黑名单
 * - GET /ask-code: 获取邮箱验证码
 * - POST /reset-password: 重置密码
 */
package com.msz.resume.ai.auth.controller;

import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.JwtUtils;
import com.msz.resume.ai.auth.common.RestBean;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.service.AccountService;
import com.msz.resume.ai.auth.service.MailService;
import com.msz.resume.ai.auth.vo.AuthorizeVO;
import com.msz.resume.ai.auth.vo.LoginVO;
import com.msz.resume.ai.auth.vo.RegisterVO;
import com.msz.resume.ai.auth.vo.ResetPasswordVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final MailService mailService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthController(AccountService accountService,
                          MailService mailService,
                          AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils) {
        this.accountService = accountService;
        this.mailService = mailService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 用户注册
     * 需要先调用 /ask-code 获取验证码
     */
    @PostMapping("/register")
    public RestBean<Void> register(@RequestBody @Valid RegisterVO vo) {
        String error = accountService.register(vo);
        return error == null ? RestBean.success("注册成功") : RestBean.badRequest(error);
    }

    /**
     * 重置密码
     * 需要先调用 /ask-code?type=reset 获取验证码
     */
    @PostMapping("/reset-password")
    public RestBean<Void> resetPassword(@RequestBody @Valid ResetPasswordVO vo) {
        String error = accountService.resetPassword(vo);
        return error == null ? RestBean.success("重置密码成功") : RestBean.badRequest(error);
    }

    /**
     * 获取邮箱验证码
     * @param email 邮箱地址
     * @param type 验证码类型：register(注册) / reset(重置密码) / modify(修改邮箱)
     */
    @GetMapping("/ask-code")
    public RestBean<Void> askCode(
            @RequestParam @Email(message = "邮箱格式不正确") String email,
            @RequestParam @Pattern(regexp = "register|reset|modify", message = "验证码类型无效") String type) {
        String error = mailService.sendVerificationCode(type, email);
        return error == null ? RestBean.success() : RestBean.badRequest(error);
    }

    /**
     * 用户登录
     * 验证用户名密码后生成 JWT Token 返回
     * 支持用户名或邮箱登录
     * remember=true 时 Token 有效期延长至 30 天
     */
    @PostMapping("/login")
    public RestBean<AuthorizeVO> login(@RequestBody @Valid LoginVO vo) {
        // 1. Spring Security 验证用户名密码
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(vo.getUsername(), vo.getPassword()));
        User user = (User) authentication.getPrincipal();

        // 2. 查询账户详情
        Account account = accountService.findByUsernameOrEmail(user.getUsername());
        if (account == null) {
            return RestBean.unauthorized("用户信息不存在");
        }

        // 3. 计算过期时间（记住我 = 30天，否则使用默认配置）
        int expireHours = Boolean.TRUE.equals(vo.getRemember()) ? 24 * 30 : jwtUtils.getExpireHours();

        // 4. 生成 JWT Token
        String token = jwtUtils.createToken(user, account.getUsername(), account.getId(), expireHours);

        // 5. 构建响应
        AuthorizeVO response = new AuthorizeVO();
        response.setId(account.getId());
        response.setUsername(account.getUsername());
        response.setEmail(account.getEmail());
        response.setToken(token);
        response.setExpire(jwtUtils.getExpireTime(expireHours));
        return RestBean.success(response);
    }

    /**
     * 获取当前登录用户信息
     *
     * 为前端刷新后的登录态恢复提供兼容接口。
     */
    @GetMapping("/me")
    public RestBean<AuthorizeVO> me(@RequestAttribute(value = Const.ATTR_USER_ID, required = false) Integer userId) {
        if (userId == null) {
            return RestBean.unauthorized("未登录");
        }

        Account account = accountService.findById(userId);
        if (account == null) {
            return RestBean.unauthorized("用户不存在");
        }

        AuthorizeVO response = new AuthorizeVO();
        response.setId(account.getId());
        response.setUsername(account.getUsername());
        response.setEmail(account.getEmail());
        return RestBean.success(response);
    }

    /**
     * 退出登录
     * 将 Token 加入黑名单，使其失效
     */
    @PostMapping("/logout")
    public RestBean<Void> logout(jakarta.servlet.http.HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return jwtUtils.invalidateToken(authorization)
                ? RestBean.success("退出登录成功")
                : RestBean.badRequest("退出登录失败，Token 无效");
    }
}
