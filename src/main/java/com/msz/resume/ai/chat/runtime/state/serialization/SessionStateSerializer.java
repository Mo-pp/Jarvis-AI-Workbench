package com.msz.resume.ai.chat.runtime.state.serialization;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import com.msz.resume.ai.chat.compression.model.LlmContextCheckpoint;
import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.serializer.StateSerializer;

import java.io.*;
import java.util.*;

/**
 * SessionState 状态序列化器。
 *
 * 用于 AskUserQuestion 挂起会话时将外层状态序列化到 Redis，
 * 相比 QueryLoopStateSerializer 额外处理 UserProfile 和嵌套的 QueryLoopState。
 */
public class SessionStateSerializer extends StateSerializer<SessionState> {

    public SessionStateSerializer() {
        super(SessionState::new);
    }

    /** 将状态数据序列化输出 */
    @Override
    public void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        out.writeObject(toSerializableMap(data));
    }

    /** 从输入反序列化状态数据 */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> readData(ObjectInput in) throws IOException, ClassNotFoundException {
        Map<String, Object> data = (Map<String, Object>) in.readObject();
        return fromSerializableMap(data);
    }

    /** 将任意对象转换为可序列化格式（处理消息类型、UserProfile、QueryLoopState等） */
    @SuppressWarnings("unchecked")
    private static Object toSerializable(Object value) {
        if (value == null) return null;
        // 单独的ToolExecutionRequest（TOOL_USE_CONTEXT字段可能直接存这个）
        if (value instanceof ToolExecutionRequest req) {
            Map<String, String> m = new HashMap<>();
            m.put("@type", "ToolExecutionRequest");
            m.put("id", req.id());
            m.put("name", req.name());
            m.put("arguments", req.arguments());
            return m;
        } else if (value instanceof OpenVikingIdentity identity) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "OpenVikingIdentity");
            m.put("account", identity.account());
            m.put("user", identity.user());
            m.put("agent", identity.agent());
            return m;
        } else if (value instanceof LlmContextCheckpoint checkpoint) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "LlmContextCheckpoint");
            m.put("tailStartIndex", checkpoint.tailStartIndex());
            m.put("sourceMessageCount", checkpoint.sourceMessageCount());
            m.put("summaryMessages", toSerializable(checkpoint.summaryMessages()));
            m.put("originalTokens", checkpoint.originalTokens());
            m.put("compactedTokens", checkpoint.compactedTokens());
            return m;
        } else if (value instanceof UserProfile ctx) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "UserProfile");
            m.put("userId", ctx.userId());
            m.put("username", ctx.username());
            m.put("role", ctx.role());
            m.put("language", ctx.language());
            m.put("outputStyle", ctx.outputStyle());
            m.put("businessContext", ctx.businessContext());
            return m;
        } else if (value instanceof QueryLoopState qls) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "QueryLoopState");
            Map<String, Object> innerData = new HashMap<>();
            for (Map.Entry<String, Object> entry : qls.data().entrySet()) {
                innerData.put(entry.getKey(), toSerializable(entry.getValue()));
            }
            m.put("data", innerData);
            return m;
        } else if (value instanceof UserMessage msg) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "UserMessage");
            m.put("text", ChatMessageTextExtractor.userText(msg));
            if (!msg.hasSingleText()) {
                List<Map<String, Object>> contents = new ArrayList<>();
                for (Content content : msg.contents()) {
                    if (content instanceof TextContent textContent) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("type", "text");
                        item.put("text", textContent.text());
                        contents.add(item);
                    } else if (content instanceof ImageContent imageContent) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("type", "image");
                        item.put("base64Data", imageContent.image().base64Data());
                        item.put("mimeType", imageContent.image().mimeType());
                        contents.add(item);
                    }
                }
                m.put("contents", contents);
            }
            if (msg.attributes() != null && !msg.attributes().isEmpty()) {
                m.put("attributes", toSerializable(msg.attributes()));
            }
            return m;
        } else if (value instanceof AiMessage msg) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "AiMessage");
            // Dashscope API 要求 content 不能为 null，序列化时将 null 转为空字符串
            m.put("text", msg.text() != null ? msg.text() : "");
            if (msg.toolExecutionRequests() != null && !msg.toolExecutionRequests().isEmpty()) {
                List<Map<String, String>> reqs = new ArrayList<>();
                for (var req : msg.toolExecutionRequests()) {
                    Map<String, String> r = new HashMap<>();
                    r.put("id", req.id());
                    r.put("name", req.name());
                    r.put("arguments", req.arguments());
                    reqs.add(r);
                }
                m.put("toolExecutionRequests", reqs);
            }
            return m;
        } else if (value instanceof SystemMessage msg) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "SystemMessage");
            m.put("text", msg.text());
            return m;
        } else if (value instanceof ToolExecutionResultMessage msg) {
            Map<String, Object> m = new HashMap<>();
            m.put("@type", "ToolExecutionResultMessage");
            m.put("id", msg.id());
            m.put("toolName", msg.toolName());
            m.put("text", msg.text());
            return m;
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(toSerializable(item));
            }
            return result;
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), toSerializable(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    /** 从可序列化格式还原为原始对象类型 */
    @SuppressWarnings("unchecked")
    private static Object fromSerializable(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("@type")) {
                String type = (String) map.get("@type");
                return switch (type) {
                    case "ToolExecutionRequest" -> ToolExecutionRequest.builder()
                            .id((String) map.get("id"))
                            .name((String) map.get("name"))
                            .arguments((String) map.get("arguments"))
                            .build();
                    case "OpenVikingIdentity" -> new OpenVikingIdentity(
                            (String) map.get("account"),
                            (String) map.get("user"),
                            (String) map.get("agent")
                    );
                    case "LlmContextCheckpoint" -> new LlmContextCheckpoint(
                            intValue(map.get("tailStartIndex")),
                            intValue(map.get("sourceMessageCount")),
                            (List<dev.langchain4j.data.message.ChatMessage>) fromSerializable(map.get("summaryMessages")),
                            intValue(map.get("originalTokens")),
                            intValue(map.get("compactedTokens"))
                    );
                    case "UserProfile" -> UserProfile.builder()
                            .userId((String) map.get("userId"))
                            .username((String) map.get("username"))
                            .role((String) map.get("role"))
                            .language((String) map.get("language"))
                            .outputStyle((String) map.get("outputStyle"))
                            .businessContext((Map<String, Object>) fromSerializable(map.get("businessContext")))
                            .build();
                    case "QueryLoopState" -> {
                        Map<String, Object> innerData = (Map<String, Object>) map.get("data");
                        Map<String, Object> restored = new HashMap<>();
                        for (Map.Entry<String, Object> entry : innerData.entrySet()) {
                            restored.put(entry.getKey(), fromSerializable(entry.getValue()));
                        }
                        yield new QueryLoopState(restored);
                    }
                    case "UserMessage" -> {
                        List<Map<String, Object>> contentMaps = (List<Map<String, Object>>) map.get("contents");
                        if (contentMaps != null && !contentMaps.isEmpty()) {
                            List<Content> contents = new ArrayList<>();
                            for (Map<String, Object> item : contentMaps) {
                                String contentType = String.valueOf(item.get("type"));
                                if ("image".equals(contentType) && item.get("base64Data") != null) {
                                    contents.add(ImageContent.from((String) item.get("base64Data"), (String) item.get("mimeType")));
                                } else if ("text".equals(contentType)) {
                                    contents.add(TextContent.from((String) item.getOrDefault("text", "")));
                                }
                            }
                            UserMessage.Builder builder = UserMessage.builder().contents(contents);
                            Object attributes = fromSerializable(map.get("attributes"));
                            if (attributes instanceof Map<?, ?> attributesMap) {
                                Map<String, Object> normalized = new HashMap<>();
                                for (Map.Entry<?, ?> entry : attributesMap.entrySet()) {
                                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                                }
                                builder.attributes(normalized);
                            }
                            yield builder.build();
                        }
                        yield new UserMessage((String) map.get("text"));
                    }
                    case "AiMessage" -> {
                        // Dashscope API 要求 content 不能为 null，反序列化时兜底空字符串
                        String text = map.get("text") != null ? (String) map.get("text") : "";
                        List<Map<String, String>> reqs = (List<Map<String, String>>) map.get("toolExecutionRequests");
                        if (reqs != null && !reqs.isEmpty()) {
                            List<ToolExecutionRequest> toolReqs = new ArrayList<>();
                            for (Map<String, String> r : reqs) {
                                toolReqs.add(ToolExecutionRequest.builder()
                                        .id(r.get("id"))
                                        .name(r.get("name"))
                                        .arguments(r.get("arguments"))
                                        .build());
                            }
                            yield AiMessage.aiMessage(text, toolReqs);
                        }
                        yield new AiMessage(text);
                    }
                    case "SystemMessage" -> new SystemMessage((String) map.get("text"));
                    case "ToolExecutionResultMessage" -> new ToolExecutionResultMessage(
                            (String) map.get("id"), (String) map.get("toolName"), (String) map.get("text"));
                    default -> {
                        Map<String, Object> result = new HashMap<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            result.put(String.valueOf(entry.getKey()), fromSerializable(entry.getValue()));
                        }
                        yield result;
                    }
                };
            } else {
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), fromSerializable(entry.getValue()));
                }
                return result;
            }
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(fromSerializable(item));
            }
            return result;
        }
        return value;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 将状态 Map 转换为可序列化 Map */
    private static Map<String, Object> toSerializableMap(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result.put(entry.getKey(), toSerializable(entry.getValue()));
        }
        return result;
    }

    /** 从可序列化 Map 还原为状态 Map */
    private static Map<String, Object> fromSerializableMap(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result.put(entry.getKey(), fromSerializable(entry.getValue()));
        }
        return result;
    }
}
