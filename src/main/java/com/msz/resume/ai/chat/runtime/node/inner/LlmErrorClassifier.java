package com.msz.resume.ai.chat.runtime.node.inner;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;

import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * LLM 异常分类器。
 *
 * <p>将不同 provider/网关抛出的底层异常统一归类，供 ErrorRecoveryNode 决定重试策略。
 */
public final class LlmErrorClassifier {

    private LlmErrorClassifier() {
    }

    public static LlmErrorType classify(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = safeLowerMessage(throwable) + " " + safeLowerMessage(root);

        if (message.contains("api_key_quota_exhausted")
                || message.contains("quota exhausted")
                || message.contains("quota_exhausted")
                || message.contains("insufficient quota")
                || message.contains("额度")
                || message.contains("余额不足")) {
            return LlmErrorType.RATE_LIMIT;
        }

        if (throwable instanceof RateLimitException || root instanceof RateLimitException
                || message.contains("rate limit") || message.contains("429")) {
            return LlmErrorType.RATE_LIMIT;
        }

        if (throwable instanceof TimeoutException || root instanceof TimeoutException
                || message.contains("timed out") || message.contains("timeout")) {
            return LlmErrorType.TIMEOUT;
        }

        if (throwable instanceof InvalidRequestException || root instanceof InvalidRequestException
                || throwable instanceof IllegalArgumentException || root instanceof IllegalArgumentException
                || message.contains("invalid_request") || message.contains("bad request")
                || message.contains("schema") || message.contains("json parse")
                || message.contains("json schema") || message.contains("400")) {
            return LlmErrorType.BAD_REQUEST_OR_SCHEMA;
        }

        if (throwable instanceof InternalServerException || root instanceof InternalServerException
                || throwable instanceof HttpException || root instanceof HttpException
                || message.contains("bad_response_status_code")
                || message.contains("status_code=502")
                || message.contains("status code: 502")
                || message.contains("status code 502")
                || message.contains("502")
                || message.contains("503")
                || message.contains("504")) {
            return LlmErrorType.UPSTREAM_5XX;
        }

        return LlmErrorType.UNKNOWN;
    }

    public static String summarize(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String className = root != null ? root.getClass().getSimpleName() : throwable.getClass().getSimpleName();
        String message = root != null ? root.getMessage() : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return className;
        }
        return className + ": " + message;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeLowerMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "";
        }
        return throwable.getMessage().toLowerCase(Locale.ROOT);
    }
}
