package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import org.springframework.stereotype.Component;

/**
 * 把 JARVIS 账户映射到 OpenViking 身份。
 */
@Component
public class OpenVikingIdentityResolver {

    private static final String DEFAULT_AGENT = "jarvis";
    private static final String TEST_USER = "u-demo-001";
    private static final String TEST_ACCOUNT = "jarvis";

    public OpenVikingIdentity resolve(Account account) {
        if (account == null || account.getUsername() == null || account.getUsername().isBlank()) {
            throw new IllegalArgumentException("OpenViking identity resolve failed: account username is blank.");
        }

        String username = account.getUsername().trim();
        String accountId = TEST_USER.equals(username) ? TEST_ACCOUNT : username;
        return new OpenVikingIdentity(accountId, username, DEFAULT_AGENT);
    }
}
