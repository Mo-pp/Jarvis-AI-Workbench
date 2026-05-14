package com.msz.resume.ai.chat.runtime.node.outer;

import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.session.OpenVikingSessionGateway;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 会话初始化节点：处理新会话/恢复会话
 */
@Slf4j
@Component
public class SessionInitNode implements AsyncNodeAction<SessionState> {

    private final OpenVikingSessionGateway openVikingSessionGateway;

    @Autowired
    public SessionInitNode(OpenVikingSessionGateway openVikingSessionGateway) {
        this.openVikingSessionGateway = openVikingSessionGateway;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SessionState currentState) {

        OpenVikingIdentity identity = OpenVikingIdentitySupport.fromSessionState(currentState);
        return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
            String sessionId = currentState.getSessionId();
            log.info("[会话初始化] 会话ID: {}", sessionId);

            // 1. 检查会话是否超时
            if (currentState.isTimeout()) {
                log.warn("[会话超时] 会话ID: {}", sessionId);
                return Map.of(SessionState.IS_ACTIVE, false);
            }

            // 2. 确保 OpenViking Session 存在（最佳努力，失败不影响主流程）
            try {
                boolean ensured = openVikingSessionGateway.ensureSession(sessionId, identity);
                if (ensured) {
                    log.info("[OpenViking] Session 已就绪: {}", sessionId);
                } else {
                    log.debug("[OpenViking] Session 确保失败或未启用: {}", sessionId);
                }
            } catch (Exception e) {
                log.warn("[OpenViking] ensureSession 异常: sessionId={}, error={}", sessionId, e.getMessage());
            }

            //获取初始化消息历史  可能用作持久化
            List<ChatMessage> intiMessages = currentState.getInitMessages() ;

            // 3. 初始化内层状态（如果是新会话）
            QueryLoopState innerState = currentState.getInnerState();
            if (innerState == null) {
                innerState = new QueryLoopState(Map.of());
            }

            // 4. 更新最后活跃时间
            java.time.Instant lastActive = java.time.Instant.now();

            Map<String, Object> update = new java.util.HashMap<>();
            update.put(SessionState.INNER_STATE, innerState);
            update.put(SessionState.LAST_ACTIVE_AT, lastActive);
            update.put(SessionState.IS_ACTIVE, true);
            update.put(SessionState.OPENVIKING_IDENTITY, identity);
            return update;
        });

    }
}
