package com.msz.resume.ai.auth.support;

import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从当前请求解析登录账户。
 */
@Component
@RequiredArgsConstructor
public class CurrentAccountResolver {

    private final AccountService accountService;

    public Account requireCurrentAccount(HttpServletRequest request, String endpoint) {
        Object userIdAttr = request.getAttribute(Const.ATTR_USER_ID);
        if (!(userIdAttr instanceof Integer userId)) {
            throw new IllegalArgumentException("未登录，无法调用 /api/claude/" + endpoint);
        }

        Account account = accountService.findById(userId);
        if (account == null) {
            throw new IllegalArgumentException("当前登录用户不存在，无法调用 /api/claude/" + endpoint);
        }
        return account;
    }
}
