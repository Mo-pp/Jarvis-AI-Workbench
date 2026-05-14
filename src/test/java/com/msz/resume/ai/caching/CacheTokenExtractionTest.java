package com.msz.resume.ai.chat.observability.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.shared.Usage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.openai.internal.OpenAiUtils.tokenUsageFrom;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存 Token 提取测试
 *
 * <p>验证从 JSON 响应到 OpenAiTokenUsage 的完整解析流程
 */
class CacheTokenExtractionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("验证 JSON 反序列化 Usage 包含 cached_tokens")
    void testJsonDeserialization() throws Exception {
        // 模拟智谱 AI 返回的 usage JSON
        String json = """
            {
                "total_tokens": 13703,
                "prompt_tokens": 12953,
                "completion_tokens": 750,
                "prompt_tokens_details": {
                    "cached_tokens": 12365
                }
            }
            """;

        // 反序列化
        Usage usage = objectMapper.readValue(json, Usage.class);

        System.out.println("=== 反序列化结果 ===");
        System.out.println("Usage: " + usage);
        System.out.println("promptTokens: " + usage.promptTokens());
        System.out.println("promptTokensDetails: " + usage.promptTokensDetails());

        // 验证
        assertNotNull(usage);
        assertEquals(12953, usage.promptTokens());
        assertEquals(750, usage.completionTokens());
        assertNotNull(usage.promptTokensDetails());
        assertEquals(12365, usage.promptTokensDetails().cachedTokens());
    }

    @Test
    @DisplayName("验证 OpenAiTokenUsage 构建包含 cachedTokens")
    void testOpenAiTokenUsageBuild() {
        // 手动构建 OpenAiTokenUsage
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(12953)
                .outputTokenCount(750)
                .totalTokenCount(13703)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(12365)
                        .build())
                .build();

        System.out.println("=== OpenAiTokenUsage 构建结果 ===");
        System.out.println("tokenUsage: " + tokenUsage);
        System.out.println("inputTokenCount: " + tokenUsage.inputTokenCount());
        System.out.println("inputTokensDetails: " + tokenUsage.inputTokensDetails());
        System.out.println("cachedTokens: " + tokenUsage.inputTokensDetails().cachedTokens());

        // 验证
        assertEquals(12953, tokenUsage.inputTokenCount());
        assertEquals(750, tokenUsage.outputTokenCount());
        assertNotNull(tokenUsage.inputTokensDetails());
        assertEquals(12365, tokenUsage.inputTokensDetails().cachedTokens());
    }

    @Test
    @DisplayName("模拟完整的响应解析流程")
    void testFullResponseParsing() throws Exception {
        // 模拟完整的 Chat Completion 响应 JSON
        String responseJson = """
            {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "created": 1677652288,
                "model": "glm-4.7",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello! How can I help you today?"
                    },
                    "finish_reason": "stop"
                }],
                "usage": {
                    "prompt_tokens": 12953,
                    "completion_tokens": 750,
                    "total_tokens": 13703,
                    "prompt_tokens_details": {
                        "cached_tokens": 12365
                    }
                }
            }
            """;

        // 只解析 usage 部分
        var root = objectMapper.readTree(responseJson);
        var usageNode = root.get("usage");

        Usage usage = objectMapper.treeToValue(usageNode, Usage.class);

        System.out.println("=== 完整响应解析 ===");
        System.out.println("Usage parsed: " + usage);
        System.out.println("cachedTokens: " + usage.promptTokensDetails().cachedTokens());

        // 验证缓存 token 正确解析
        assertEquals(12365, usage.promptTokensDetails().cachedTokens());
    }

    @Test
    @DisplayName("测试 cached_tokens 为 null 的情况")
    void testNullCachedTokens() throws Exception {
        String json = """
            {
                "total_tokens": 1500,
                "prompt_tokens": 1000,
                "completion_tokens": 500
            }
            """;

        Usage usage = objectMapper.readValue(json, Usage.class);

        System.out.println("=== 无 cached_tokens 的响应 ===");
        System.out.println("Usage: " + usage);
        System.out.println("promptTokensDetails: " + usage.promptTokensDetails());

        // promptTokensDetails 应该为 null
        assertNull(usage.promptTokensDetails());
    }

    @Test
    @DisplayName("端到端：ChatCompletionResponse JSON -> OpenAiTokenUsage")
    void testEndToEndParsing() throws Exception {
        // 模拟智谱 AI 完整响应
        String responseJson = """
            {
                "id": "20260422204720abc123",
                "object": "chat.completion",
                "created": 1713787640,
                "model": "glm-4.7",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "你好！我是智能助手。"
                    },
                    "finish_reason": "stop"
                }],
                "usage": {
                    "prompt_tokens": 12953,
                    "completion_tokens": 750,
                    "total_tokens": 13703,
                    "prompt_tokens_details": {
                        "cached_tokens": 12365
                    }
                }
            }
            """;

        // 完整反序列化
        ChatCompletionResponse response = objectMapper.readValue(responseJson, ChatCompletionResponse.class);

        System.out.println("=== 端到端解析 ===");
        System.out.println("ChatCompletionResponse id: " + response.id());
        System.out.println("Usage: " + response.usage());

        // 使用 OpenAiUtils.tokenUsageFrom 转换
        OpenAiTokenUsage tokenUsage = tokenUsageFrom(response.usage());

        System.out.println("OpenAiTokenUsage: " + tokenUsage);
        System.out.println("inputTokenCount: " + tokenUsage.inputTokenCount());
        System.out.println("inputTokensDetails: " + tokenUsage.inputTokensDetails());
        System.out.println("cachedTokens: " + tokenUsage.inputTokensDetails().cachedTokens());

        // 验证完整链路
        assertNotNull(response.usage());
        assertEquals(12953, response.usage().promptTokens());
        assertNotNull(response.usage().promptTokensDetails());
        assertEquals(12365, response.usage().promptTokensDetails().cachedTokens());

        // 验证转换后的 OpenAiTokenUsage
        assertNotNull(tokenUsage);
        assertEquals(12953, tokenUsage.inputTokenCount());
        assertNotNull(tokenUsage.inputTokensDetails());
        assertEquals(12365, tokenUsage.inputTokensDetails().cachedTokens());
    }
}
