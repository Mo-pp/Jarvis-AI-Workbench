package com.msz.resume.ai.integrations.openviking.core.dto;

/**
 * OpenViking 管理接口 - 创建 account 响应。
 */
public record OpenVikingAdminCreateAccountResponse(
        String status,
        Result result,
        ErrorDetail error,
        Object telemetry
) {

    public record Result(
            String account_id,
            String admin_user_id,
            String user_key,
            Boolean isolate_user_scope_by_agent,
            Boolean isolate_agent_scope_by_user
    ) {
    }

    public record ErrorDetail(
            String code,
            String message
    ) {
    }
}
