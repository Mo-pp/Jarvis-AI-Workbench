package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
/**
 * 错误恢复节点：处理5类错误（用户停止、响应超时、工具失败、Token超限、API速率限制）
 */
@Slf4j
@Component
public class ErrorRecoveryNode implements AsyncNodeAction<QueryLoopState> {


    @Override
    public CompletableFuture<Map<String, Object>> apply(QueryLoopState currentState) {

        OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
        return OpenVikingIdentitySupport.supplyAsync(identity, () -> {

            int retryCount = currentState.getMaxTokenRecoveryCount();
            log.info("(重试次数)ErrorRecoveryNode: retryCount={}, errorType={}, errorMessage={}",
                    retryCount, currentState.getErrorType(), currentState.getErrorMessage());


            //1. 获取错误类型，从TRANSITION里拿
            String errorType = currentState.getTransition();

            //2. 错误类型为空，则返回
            if (errorType == null || errorType.isEmpty()) {
                return Map.of();
            }

            // ---------------------- 第一种错误：用户停止了 ----------------------
            if ("user_stopped".equals(errorType)) {
                // 用户取消请求了，直接标记结束
                return Map.of(QueryLoopState.TRANSITION, "terminate");
            }



            // ---------------------- 第二种错误：响应超时 ----------------------
            if ("timeout".equals(errorType)) {
                // 最多重试1次
                if (retryCount < 1) {
                    return Map.of(
                            QueryLoopState.MAX_TOKEN_RECOVERY_COUNT, retryCount + 1,
                            QueryLoopState.TRANSITION, "retry"
                    );
                } else {
                    return Map.of(QueryLoopState.TRANSITION, "terminate");
                }
            }


            // ---------------------- 第三种错误：工具调用失败（含MCP） ----------------------
            if ("tool_failed".equals(errorType)) {
                Exception error = (Exception) currentState.getToolUseContext().get(0);
                // 临时错误的话，重试1次
                if (isTemporaryError(error) && retryCount < 1) {
                    return Map.of(
                            QueryLoopState.MAX_TOKEN_RECOVERY_COUNT, retryCount + 1,
                            QueryLoopState.TRANSITION, "retry"
                    );
                } else {
                    return Map.of(QueryLoopState.TRANSITION, "terminate");
                }
            }

            // ---------------------- 第四种错误：上下文Token超限 ----------------------
            if ("token_overflow".equals(errorType)){
                // 还没试过压缩，而且重试次数没到3次
                if(retryCount<3&&!currentState.hasCompacted()){
                    // 尝试压缩
                    /**
                     * 压缩方法：待实现
                     */

                    //
                    // 追踪存在AUTO_COMPACT_TRACKING里
                    List<Object> compactTrack = compactOldMessages( currentState.getMessages());

                    return Map.of(
                            QueryLoopState.HAS_ATTEMPTED_COMPACT, true,
                            QueryLoopState.MAX_TOKEN_RECOVERY_COUNT, retryCount + 1,
                            QueryLoopState.AUTO_COMPACT_TRACKING, compactTrack,
                            QueryLoopState.TRANSITION, "retry"
                    );
                } else {
                    return Map.of(QueryLoopState.TRANSITION, "terminate");
                }

            }

            // ---------------------- 第五种错误：API速率限制 ----------------------
            if ("error".equals(errorType)) {
                return recoverLlmError(currentState, retryCount);
            }




            // 未知错误，直接结束
            log.info("ErrorRecoveryNode: 未知错误，直接结束");
            return Map.of(QueryLoopState.TRANSITION, "terminate");
        });

    }


    // ---------------------- 辅助方法 ----------------------
    private boolean isTemporaryError(Exception error) {
        String msg = error.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("Connection reset")
                || msg.contains("timeout")
                || msg.contains("Connection refused")
                || msg.contains("MCP connection broken");
    }

    private Map<String, Object> recoverLlmError(QueryLoopState currentState, int retryCount) {
        String errorType = currentState.getErrorType();
        if (LlmErrorType.RATE_LIMIT.getValue().equals(errorType)) {
            if (isQuotaExhausted(currentState.getErrorMessage())) {
                log.error("[ErrorRecoveryNode] API key 额度耗尽，终止且不重试: {}", currentState.getErrorMessage());
                return Map.of(QueryLoopState.TRANSITION, "terminate");
            }
            return retryWithDelay("API限流", retryCount, new long[]{15, 20});
        }

        if (LlmErrorType.UPSTREAM_5XX.getValue().equals(errorType)) {
            return retryWithDelay("上游 5xx 错误", retryCount, new long[]{1});
        }

        if (LlmErrorType.TIMEOUT.getValue().equals(errorType)) {
            return retryWithDelay("LLM调用超时", retryCount, new long[]{2});
        }

        if (LlmErrorType.BAD_REQUEST_OR_SCHEMA.getValue().equals(errorType)) {
            log.error("[ErrorRecoveryNode] 请求格式或 schema 错误，不重试: {}", currentState.getErrorMessage());
            return Map.of(QueryLoopState.TRANSITION, "terminate");
        }

        return retryWithDelay("未知 LLM 错误", retryCount, new long[]{1});
    }

    private boolean isQuotaExhausted(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("api_key_quota_exhausted")
                || normalized.contains("quota exhausted")
                || normalized.contains("quota_exhausted")
                || normalized.contains("insufficient quota")
                || normalized.contains("额度")
                || normalized.contains("余额不足");
    }

    private Map<String, Object> retryWithDelay(String reason, int retryCount, long[] delaysSeconds) {
        if (retryCount >= delaysSeconds.length) {
            log.error("[ErrorRecoveryNode] {}重试次数耗尽，终止", reason);
            return Map.of(QueryLoopState.TRANSITION, "terminate");
        }

        long delaySeconds = delaysSeconds[retryCount];
        log.warn("[ErrorRecoveryNode] {}，准备延迟重试: retryCount={}, delay={}s",
                reason, retryCount, delaySeconds);
        sleepSeconds(delaySeconds);
        return Map.of(
                QueryLoopState.MAX_TOKEN_RECOVERY_COUNT, retryCount + 1,
                QueryLoopState.TRANSITION, "retry"
        );
    }

    void sleepSeconds(long delaySeconds) {
        try {
            Thread.sleep(delaySeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // TODO: 此方法为 stub，待接入 MessagePreprocessingPipeline 的压缩逻辑
    // 当前 token_overflow 走的是 Autocompact（L5），此处保留作为备用恢复路径
    private List<Object> compactOldMessages(List<ChatMessage> messages) {
        List<Object> track = new ArrayList<>();
        track.add("压缩了前10条旧消息");
        return track;
    }
}
