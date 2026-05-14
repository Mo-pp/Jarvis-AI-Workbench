package com.msz.resume.ai.integrations.openviking.core.context;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Helpers for carrying OpenViking tenant identity across explicit state and async boundaries.
 */
public final class OpenVikingIdentitySupport {

    private OpenVikingIdentitySupport() {
    }

    public static OpenVikingIdentity fromSessionState(SessionState state) {
        if (state == null) {
            return null;
        }
        OpenVikingIdentity identity = state.getOpenVikingIdentity();
        if (identity != null) {
            return identity;
        }
        QueryLoopState innerState = state.getInnerState();
        if (innerState != null) {
            identity = fromQueryLoopState(innerState);
            if (identity != null) {
                return identity;
            }
        }
        return fromUserProfile(state.getUserContext());
    }

    public static OpenVikingIdentity fromQueryLoopState(QueryLoopState state) {
        if (state == null) {
            return null;
        }
        OpenVikingIdentity identity = state.getOpenVikingIdentity();
        if (identity != null) {
            return identity;
        }
        return fromUserProfile(state.getUserContext());
    }

    public static OpenVikingIdentity fromUserProfile(UserProfile userProfile) {
        if (userProfile == null || userProfile.businessContext() == null) {
            return null;
        }
        Map<String, Object> businessContext = userProfile.businessContext();
        String account = asText(businessContext.get("openVikingAccount"));
        String user = asText(businessContext.get("openVikingUser"));
        String agent = asText(businessContext.get("openVikingAgent"));
        if (!hasText(account) || !hasText(user) || !hasText(agent)) {
            return null;
        }
        return new OpenVikingIdentity(account, user, agent);
    }

    public static <T> CompletableFuture<T> supplyAsync(OpenVikingIdentity identity, Supplier<T> supplier) {
        OpenVikingIdentity captured = identity != null ? identity : OpenVikingIdentityContextHolder.get();
        return CompletableFuture.supplyAsync(() -> withIdentity(captured, supplier));
    }

    public static <T> T withIdentity(OpenVikingIdentity identity, Supplier<T> supplier) {
        OpenVikingIdentity previous = OpenVikingIdentityContextHolder.get();
        try {
            if (identity != null) {
                OpenVikingIdentityContextHolder.set(identity);
            }
            return supplier.get();
        } finally {
            if (previous != null) {
                OpenVikingIdentityContextHolder.set(previous);
            } else {
                OpenVikingIdentityContextHolder.clear();
            }
        }
    }

    private static String asText(Object value) {
        return value instanceof String text ? text : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
