package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorRecoveryNodeTest {

    @Test
    @DisplayName("rate_limit 第一次错误应进入 retry 并递增计数")
    void shouldRetryRateLimitOnFirstFailure() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState state = state("error", LlmErrorType.RATE_LIMIT.getValue(), 0);

        Map<String, Object> result = node.apply(state).join();

        assertEquals("retry", result.get(QueryLoopState.TRANSITION));
        assertEquals(1, result.get(QueryLoopState.MAX_TOKEN_RECOVERY_COUNT));
        assertEquals(15L, node.lastDelaySeconds);
    }

    @Test
    @DisplayName("rate_limit 超过两次重试后应 terminate")
    void shouldTerminateRateLimitWhenBudgetExhausted() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState state = state("error", LlmErrorType.RATE_LIMIT.getValue(), 2);

        Map<String, Object> result = node.apply(state).join();

        assertEquals("terminate", result.get(QueryLoopState.TRANSITION));
    }

    @Test
    @DisplayName("API key 额度耗尽不应重试")
    void shouldTerminateQuotaExhaustedWithoutRetry() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState state = state(
                "error",
                LlmErrorType.RATE_LIMIT.getValue(),
                "HttpException: {\"code\":\"API_KEY_QUOTA_EXHAUSTED\",\"message\":\"API key 额度已用完\"}",
                0
        );

        Map<String, Object> result = node.apply(state).join();

        assertEquals("terminate", result.get(QueryLoopState.TRANSITION));
        assertEquals(-1L, node.lastDelaySeconds);
    }

    @Test
    @DisplayName("upstream_5xx 只应快速重试一次")
    void shouldRetryUpstream5xxOnce() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState first = state("error", LlmErrorType.UPSTREAM_5XX.getValue(), 0);
        QueryLoopState second = state("error", LlmErrorType.UPSTREAM_5XX.getValue(), 1);

        Map<String, Object> firstResult = node.apply(first).join();
        Map<String, Object> secondResult = node.apply(second).join();

        assertEquals("retry", firstResult.get(QueryLoopState.TRANSITION));
        assertEquals(1L, node.lastDelaySeconds);
        assertEquals("terminate", secondResult.get(QueryLoopState.TRANSITION));
    }

    @Test
    @DisplayName("timeout 只应重试一次且延迟 2 秒")
    void shouldRetryTimeoutOnce() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState first = state("error", LlmErrorType.TIMEOUT.getValue(), 0);
        QueryLoopState second = state("error", LlmErrorType.TIMEOUT.getValue(), 1);

        Map<String, Object> firstResult = node.apply(first).join();
        Map<String, Object> secondResult = node.apply(second).join();

        assertEquals("retry", firstResult.get(QueryLoopState.TRANSITION));
        assertEquals(2L, node.lastDelaySeconds);
        assertEquals("terminate", secondResult.get(QueryLoopState.TRANSITION));
    }

    @Test
    @DisplayName("bad_request_or_schema 不应重试")
    void shouldNotRetryBadRequestOrSchema() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState state = state("error", LlmErrorType.BAD_REQUEST_OR_SCHEMA.getValue(), 0);

        Map<String, Object> result = node.apply(state).join();

        assertEquals("terminate", result.get(QueryLoopState.TRANSITION));
    }

    @Test
    @DisplayName("unknown 错误应保守快速重试一次")
    void shouldRetryUnknownOnce() {
        TestableErrorRecoveryNode node = new TestableErrorRecoveryNode();
        QueryLoopState first = state("error", LlmErrorType.UNKNOWN.getValue(), 0);
        QueryLoopState second = state("error", LlmErrorType.UNKNOWN.getValue(), 1);

        Map<String, Object> firstResult = node.apply(first).join();
        Map<String, Object> secondResult = node.apply(second).join();

        assertEquals("retry", firstResult.get(QueryLoopState.TRANSITION));
        assertEquals(1L, node.lastDelaySeconds);
        assertEquals("terminate", secondResult.get(QueryLoopState.TRANSITION));
    }

    private QueryLoopState state(String transition, String errorType, int retryCount) {
        return state(transition, errorType, "test error", retryCount);
    }

    private QueryLoopState state(String transition, String errorType, String errorMessage, int retryCount) {
        Map<String, Object> initData = new HashMap<>();
        initData.put(QueryLoopState.MESSAGE_HISTORY, new ArrayList<>());
        initData.put(QueryLoopState.TRANSITION, transition);
        initData.put(QueryLoopState.ERROR_TYPE, errorType);
        initData.put(QueryLoopState.ERROR_MESSAGE, errorMessage);
        initData.put(QueryLoopState.MAX_TOKEN_RECOVERY_COUNT, retryCount);
        return new QueryLoopState(initData);
    }

    private static class TestableErrorRecoveryNode extends ErrorRecoveryNode {
        private long lastDelaySeconds = -1;

        @Override
        void sleepSeconds(long delaySeconds) {
            lastDelaySeconds = delaySeconds;
        }
    }
}
