package com.msz.resume.ai.integrations.openviking.core.context;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;

/**
 * 当前线程 OpenViking 动态身份上下文。
 */
public final class OpenVikingIdentityContextHolder {

    private static final ThreadLocal<OpenVikingIdentity> HOLDER = new ThreadLocal<>();

    private OpenVikingIdentityContextHolder() {
    }

    public static void set(OpenVikingIdentity identity) {
        HOLDER.set(identity);
    }

    public static OpenVikingIdentity get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
