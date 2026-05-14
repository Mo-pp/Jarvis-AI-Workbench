package com.msz.resume.ai.integrations.openviking.core.model;

import java.io.Serializable;

/**
 * OpenViking 请求身份。
 */
public record OpenVikingIdentity(
        String account,
        String user,
        String agent
) implements Serializable {
    public static OpenVikingIdentity empty() {
        return new OpenVikingIdentity("", "", "");
    }

    public boolean isEmpty() {
        return !hasText(account) && !hasText(user) && !hasText(agent);
    }

    public boolean isComplete() {
        return hasText(account) && hasText(user) && hasText(agent);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
