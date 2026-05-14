package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAdminCreateAccountRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAdminCreateAccountResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * OpenViking 多租户开通服务。
 */
@Service
@RequiredArgsConstructor
public class OpenVikingProvisioningService {

    private final OpenVikingClient openVikingClient;
    private final OpenVikingIdentityResolver openVikingIdentityResolver;

    public String createAdminAccountForUsername(String username) {
        String normalized = normalizeUsername(username);
        String accountId = openVikingIdentityResolver.resolve(
                new com.msz.resume.ai.auth.entity.Account(null, normalized, null, null, null, null, null)
        ).account();

        OpenVikingAdminCreateAccountRequest request = new OpenVikingAdminCreateAccountRequest(
                accountId,
                normalized,
                false,
                false
        );
        OpenVikingAdminCreateAccountResponse response = openVikingClient.createAdminAccount(request);
        if (response.result() == null || response.result().user_key() == null || response.result().user_key().isBlank()) {
            throw new OpenVikingClientException("OpenViking create admin account failed: missing admin key in response.");
        }
        return response.result().user_key().trim();
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new OpenVikingClientException("OpenViking create admin account failed: username is blank.");
        }
        return username.trim();
    }
}
