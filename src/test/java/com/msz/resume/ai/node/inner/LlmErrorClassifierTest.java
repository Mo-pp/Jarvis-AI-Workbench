package com.msz.resume.ai.chat.runtime.node.inner;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmErrorClassifierTest {

    @Test
    @DisplayName("RateLimitException 应分类为 rate_limit")
    void shouldClassifyRateLimitException() {
        LlmErrorType type = LlmErrorClassifier.classify(new RateLimitException("429 rate limit exceeded"));
        assertEquals(LlmErrorType.RATE_LIMIT, type);
    }

    @Test
    @DisplayName("API_KEY_QUOTA_EXHAUSTED 应分类为 rate_limit 而不是 upstream_5xx")
    void shouldClassifyQuotaExhaustedAsRateLimit() {
        RuntimeException exception = new RuntimeException(
                "LLM request failed",
                new RuntimeException("HttpException: {\"code\":\"API_KEY_QUOTA_EXHAUSTED\",\"message\":\"API key 额度已用完\"}")
        );

        LlmErrorType type = LlmErrorClassifier.classify(exception);

        assertEquals(LlmErrorType.RATE_LIMIT, type);
    }

    @Test
    @DisplayName("502 bad_response_status_code 应分类为 upstream_5xx")
    void shouldClassifyUpstream5xx() {
        RuntimeException exception = new RuntimeException(
                "Streaming chat request failed",
                new InternalServerException("openai_error bad_response_status_code status_code=502")
        );
        LlmErrorType type = LlmErrorClassifier.classify(exception);
        assertEquals(LlmErrorType.UPSTREAM_5XX, type);
    }

    @Test
    @DisplayName("TimeoutException 应分类为 timeout")
    void shouldClassifyTimeout() {
        RuntimeException exception = new RuntimeException(new TimeoutException("Streaming chat request timed out"));
        LlmErrorType type = LlmErrorClassifier.classify(exception);
        assertEquals(LlmErrorType.TIMEOUT, type);
    }

    @Test
    @DisplayName("InvalidRequestException 应分类为 bad_request_or_schema")
    void shouldClassifyBadRequest() {
        LlmErrorType type = LlmErrorClassifier.classify(new InvalidRequestException("invalid_request schema mismatch"));
        assertEquals(LlmErrorType.BAD_REQUEST_OR_SCHEMA, type);
    }

    @Test
    @DisplayName("未知异常应分类为 unknown")
    void shouldClassifyUnknown() {
        LlmErrorType type = LlmErrorClassifier.classify(new RuntimeException("something odd happened"));
        assertEquals(LlmErrorType.UNKNOWN, type);
    }
}
