package com.msz.resume.ai.chat.runtime.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户可见聊天时间线动作的统一拼装与发布服务。
 *
 * 作用：约束所有 timeline payload 的公共字段结构，比如 id、runId、agent 信息、
 * persistable、promptVisible、sensitive 等，避免各个事件服务各自拼装导致字段漂移。
 * 可以把它理解成“时间线动作工厂”。
 *
 * 代码逻辑：
 * 1. 提供 builder 统一初始化动作骨架
 * 2. 提供 publish 出口，负责把动作安全发到 SSE
 * 3. 内部用 AgentDefaults 和 Builder 兜底主/子 Agent 的展示字段
 */
@Slf4j
@Service
public class TimelineActionService {

    public static final String FIELD_PERSISTABLE = "persistable";
    public static final String FIELD_PROMPT_VISIBLE = "promptVisible";
    public static final String FIELD_SENSITIVE = "sensitive";

    /** 创建主 Agent 默认语义下的时间线动作构建器。 */
    public TimelineActionBuilder builder(String id,
                                         ChatRunTraceContext traceContext,
                                         TraceAgentDescriptor agentDescriptor) {
        return builder(id, traceContext, agentDescriptor, AgentDefaults.main());
    }

    /** 创建可带默认 Agent 信息的时间线动作构建器，统一补齐公共字段。 */
    public TimelineActionBuilder builder(String id,
                                         ChatRunTraceContext traceContext,
                                         TraceAgentDescriptor agentDescriptor,
                                         AgentDefaults defaults) {
        return new TimelineActionBuilder(id, traceContext, agentDescriptor, defaults != null ? defaults : AgentDefaults.main());
    }

    /** 将时间线动作发布到 SSE；如果连接断了就静默跳过，不影响主流程。 */
    public void publish(ChatRunTraceContext traceContext,
                        String eventType,
                        Map<String, Object> payload,
                        String source) {
        if (traceContext == null || !traceContext.isActive() || traceContext.sink() == null) {
            return;
        }
        try {
            traceContext.sink().send(eventType, payload);
        } catch (Exception e) {
            log.warn("[TimelineActionService] SSE send failed: source={}, type={}, id={}, error={}",
                    source, eventType, payload != null ? payload.get("id") : null, e.getMessage());
        }
    }

    /** Agent 展示字段的默认值集合，避免主/子 Agent 没传全时前端显示空白。 */
    public record AgentDefaults(String agentScope, String agentId, String agentLabel) {

        /** 返回主 Agent 默认值。 */
        public static AgentDefaults main() {
            return new AgentDefaults("main", "main", "Main Agent");
        }

        /** 返回子 Agent 默认值。 */
        public static AgentDefaults subAgent() {
            return new AgentDefaults("sub", "", "Sub Agent");
        }
    }

    /** 时间线动作构建器，用链式写法统一补公共字段。 */
    public static class TimelineActionBuilder {

        private final Map<String, Object> payload = new LinkedHashMap<>();

        /** 初始化动作骨架，把 runId、agent 字段和默认安全标记先铺好。 */
        private TimelineActionBuilder(String id,
                                      ChatRunTraceContext traceContext,
                                      TraceAgentDescriptor agentDescriptor,
                                      AgentDefaults defaults) {
            payload.put("id", stringOrBlank(id));
            payload.put("runId", traceContext != null ? stringOrBlank(traceContext.runId()) : "");
            payload.put("agentScope", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentScope() : "",
                    defaults.agentScope()
            ));
            payload.put("agentId", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentId() : "",
                    defaults.agentId()
            ));
            payload.put("agentLabel", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentLabel() : "",
                    defaults.agentLabel()
            ));
            payload.put("timestamp", Instant.now());
            payload.put(FIELD_PERSISTABLE, true);
            payload.put(FIELD_PROMPT_VISIBLE, false);
            payload.put(FIELD_SENSITIVE, false);
        }

        /** 写入工具调用 ID，让前端和历史回放都能回溯到具体工具调用。 */
        public TimelineActionBuilder toolCallId(String toolCallId) {
            payload.put("toolCallId", stringOrBlank(toolCallId));
            return this;
        }

        /** 设置时间线卡片标题，相当于这张动作卡的抬头。 */
        public TimelineActionBuilder title(String title) {
            payload.put("title", stringOrBlank(title));
            return this;
        }

        /** 设置动作状态，比如 running、success、failed。 */
        public TimelineActionBuilder status(String status) {
            payload.put("status", stringOrBlank(status));
            return this;
        }

        /** 设置给用户看的摘要，像把技术细节压成一句人话。 */
        public TimelineActionBuilder summary(String summary) {
            payload.put("summary", stringOrBlank(summary));
            return this;
        }

        /** 设置错误描述，失败卡片会直接拿它展示。 */
        public TimelineActionBuilder error(String error) {
            payload.put("error", stringOrBlank(error));
            return this;
        }

        /** 标记该动作是否需要持久化进历史回放。 */
        public TimelineActionBuilder persistable(boolean persistable) {
            payload.put(FIELD_PERSISTABLE, persistable);
            return this;
        }

        /** 标记动作文本是否应该直接暴露给提示词或前端主气泡。 */
        public TimelineActionBuilder promptVisible(boolean promptVisible) {
            payload.put(FIELD_PROMPT_VISIBLE, promptVisible);
            return this;
        }

        /** 标记动作是否包含敏感信息，便于后续做过滤。 */
        public TimelineActionBuilder sensitive(boolean sensitive) {
            payload.put(FIELD_SENSITIVE, sensitive);
            return this;
        }

        /** 写入任意扩展字段，用于各事件服务补自己的业务信息。 */
        public TimelineActionBuilder put(String key, Object value) {
            if (key != null && !key.isBlank()) {
                payload.put(key, value);
            }
            return this;
        }

        /** 批量写入扩展字段，像把一组业务属性一次性塞进动作卡片。 */
        public TimelineActionBuilder putAll(Map<String, Object> fields) {
            if (fields != null) {
                fields.forEach(this::put);
            }
            return this;
        }

        /** 构造最终 payload，返回副本避免外部继续污染内部状态。 */
        public Map<String, Object> build() {
            return new LinkedHashMap<>(payload);
        }

        /** 把 null 安全转成空字符串，避免前端字段忽有忽无。 */
        private static String stringOrBlank(String value) {
            return value != null ? value : "";
        }

        /** 两个候选值二选一，前者为空时退回默认值。 */
        private static String firstNonBlank(String first, String second) {
            return first != null && !first.isBlank() ? first : stringOrBlank(second);
        }
    }
}
