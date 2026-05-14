package com.msz.resume.ai.tool;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;

/**
 * 当前工具执行运行时上下文。
 */
public final class ToolRuntimeContext {

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<OpenVikingIdentity> CURRENT_OPENVIKING_IDENTITY = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_RUN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT_LABEL = new ThreadLocal<>();

    private ToolRuntimeContext() {
    }

    public static void setSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public static void setOpenVikingIdentity(OpenVikingIdentity identity) {
        CURRENT_OPENVIKING_IDENTITY.set(identity);
    }

    public static OpenVikingIdentity getOpenVikingIdentity() {
        return CURRENT_OPENVIKING_IDENTITY.get();
    }

    public static void setRunId(String runId) {
        CURRENT_RUN_ID.set(runId);
    }

    public static String getRunId() {
        return CURRENT_RUN_ID.get();
    }

    public static void setAgentId(String agentId) {
        CURRENT_AGENT_ID.set(agentId);
    }

    public static String getAgentId() {
        return CURRENT_AGENT_ID.get();
    }

    public static void setAgentLabel(String agentLabel) {
        CURRENT_AGENT_LABEL.set(agentLabel);
    }

    public static String getAgentLabel() {
        return CURRENT_AGENT_LABEL.get();
    }

    public static void clear() {
        CURRENT_SESSION_ID.remove();
        CURRENT_OPENVIKING_IDENTITY.remove();
        CURRENT_RUN_ID.remove();
        CURRENT_AGENT_ID.remove();
        CURRENT_AGENT_LABEL.remove();
    }
}
