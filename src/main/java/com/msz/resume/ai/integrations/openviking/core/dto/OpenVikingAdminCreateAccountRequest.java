package com.msz.resume.ai.integrations.openviking.core.dto;

/**
 * OpenViking 管理接口 - 创建 account 请求。
 */
public record OpenVikingAdminCreateAccountRequest(
        String account_id,
        String admin_user_id,
        boolean isolate_user_scope_by_agent,
        boolean isolate_agent_scope_by_user
) {
}
