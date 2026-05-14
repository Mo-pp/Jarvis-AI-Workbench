package com.msz.resume.ai.chat.runtime.node.inner;

/**
 * LLM 调用错误类型。
 *
 * <p>用于把 provider/网关返回的底层异常归一化为有限的恢复策略分支。
 */
public enum LlmErrorType {

    RATE_LIMIT("rate_limit"),
    UPSTREAM_5XX("upstream_5xx"),
    TIMEOUT("timeout"),
    BAD_REQUEST_OR_SCHEMA("bad_request_or_schema"),
    UNKNOWN("unknown");

    private final String value;

    LlmErrorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
