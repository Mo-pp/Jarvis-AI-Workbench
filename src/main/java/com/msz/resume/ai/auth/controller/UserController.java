/**
 * 用户信息控制器
 *
 * 作用：提供已登录用户的信息查询和修改功能
 *
 * 接口列表：
 * - GET /info: 获取当前登录用户信息
 * - POST /change-password: 修改密码
 *
 * 所有接口需要携带有效的 JWT Token
 */
package com.msz.resume.ai.auth.controller;

import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.common.RestBean;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.service.AccountService;
import com.msz.resume.ai.auth.vo.AuthorizeVO;
import com.msz.resume.ai.auth.vo.ChangePasswordVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AccountService accountService;

    public UserController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 获取当前登录用户信息
     * userId 由 JwtAuthenticationFilter 从 Token 中提取并存入 request
     */
    @GetMapping("/info")
    public RestBean<AuthorizeVO> info(@RequestAttribute(value = Const.ATTR_USER_ID, required = false) Integer userId) {
        if (userId == null) {
            return RestBean.unauthorized("未登录");
        }

        Account account = accountService.findById(userId);
        if (account == null) {
            return RestBean.unauthorized("用户不存在");
        }

        AuthorizeVO vo = new AuthorizeVO();
        vo.setId(account.getId());
        vo.setUsername(account.getUsername());
        vo.setEmail(account.getEmail());
        return RestBean.success(vo);
    }

    /**
     * 修改密码
     * 需要验证旧密码，新密码不能与旧密码相同
     */
    @PostMapping("/change-password")
    public RestBean<Void> changePassword(@RequestAttribute(value = Const.ATTR_USER_ID, required = false) Integer userId,
                                         @RequestBody @Valid ChangePasswordVO vo) {
        if (userId == null) {
            return RestBean.unauthorized("未登录");
        }
        String error = accountService.changePassword(userId, vo);
        return error == null ? RestBean.success("修改密码成功") : RestBean.badRequest(error);
    }
}
