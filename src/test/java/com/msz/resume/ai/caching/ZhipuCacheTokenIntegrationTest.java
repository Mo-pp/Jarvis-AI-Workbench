package com.msz.resume.ai.chat.observability.cache;

import com.msz.resume.ai.chat.llm.config.LLMConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智谱AI 缓存 Token 集成测试
 *
 * <p>使用 Spring Boot 集成测试，直接调用智谱AI API，验证 cached_tokens 是否被正确解析。
 *
 * <p>运行命令: mvn test -Dtest=ZhipuCacheTokenIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("dev")
class ZhipuCacheTokenIntegrationTest {

    @Autowired(required = false)
    @Qualifier("zhipuChatModel")
    private ChatModel chatModel;

    @Test
    @DisplayName("集成测试：调用智谱AI三次验证缓存命中")
    void testZhipuCacheTokenExtraction() {
        // 检查 ChatModel 是否可用
        if (chatModel == null) {
            System.out.println("跳过测试：zhipuChatModel 未配置（检查 jarvis.llm.provider=zhipu 和 BIGMODEL_API_KEY）");
            return;
        }

        // 第一次请求：建立缓存
        System.out.println("\n========== 第一次请求 ==========");

        String systemPrompt = "你是一个专业的数据分析师，擅长解释数据趋势和提供业务洞察。请用简洁的语言回答问题，每次回答不超过100字。";
        String userMessage = "什么是用户留存率？";

        ChatRequest request1 = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)
                ))
                .build();

        ChatResponse response1 = chatModel.chat(request1);

        System.out.println("\n=== 第一次响应 ===");
        System.out.println("AI回复: " + (response1.aiMessage().text() != null ?
                response1.aiMessage().text().substring(0, Math.min(100, response1.aiMessage().text().length())) + "..." : "null"));
        analyzeTokenUsage(response1);

        // 第二次请求：应该命中缓存（相同的系统提示词）
        System.out.println("\n========== 第二次请求（复用系统提示词）==========");

        ChatRequest request2 = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from("什么是漏斗分析？")
                ))
                .build();

        ChatResponse response2 = chatModel.chat(request2);

        System.out.println("\n=== 第二次响应 ===");
        System.out.println("AI回复: " + (response2.aiMessage().text() != null ?
                response2.aiMessage().text().substring(0, Math.min(100, response2.aiMessage().text().length())) + "..." : "null"));
        analyzeTokenUsage(response2);

        // 第三次请求：再次验证
        System.out.println("\n========== 第三次请求 ==========");

        ChatRequest request3 = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from("什么是转化率？")
                ))
                .build();

        ChatResponse response3 = chatModel.chat(request3);

        System.out.println("\n=== 第三次响应 ===");
        System.out.println("AI回复: " + (response3.aiMessage().text() != null ?
                response3.aiMessage().text().substring(0, Math.min(100, response3.aiMessage().text().length())) + "..." : "null"));
        analyzeTokenUsage(response3);

        // 验证结果
        System.out.println("\n========== 测试结论 ==========");
        System.out.println("如果看到 cachedTokens > 0，说明缓存追踪器工作正常");
        System.out.println("如果始终 cachedTokens = null 或 0，需要检查 LangChain4j 版本或智谱API响应格式");
    }

    /**
     * 分析响应中的 TokenUsage，提取 cachedTokens
     */
    private void analyzeTokenUsage(ChatResponse response) {
        if (response == null) {
            System.out.println(">>> 响应为 null");
            return;
        }

        var tokenUsage = response.tokenUsage();
        if (tokenUsage == null) {
            System.out.println(">>> tokenUsage 为 null");
            return;
        }

        System.out.println(">>> TokenUsage 类型: " + tokenUsage.getClass().getName());
        System.out.println(">>> inputTokenCount: " + tokenUsage.inputTokenCount());
        System.out.println(">>> outputTokenCount: " + tokenUsage.outputTokenCount());

        // 尝试提取 cachedTokens
        if (tokenUsage instanceof OpenAiTokenUsage openAiUsage) {
            System.out.println(">>> 这是 OpenAiTokenUsage");

            var details = openAiUsage.inputTokensDetails();
            if (details != null) {
                System.out.println(">>> inputTokensDetails: " + details);
                System.out.println(">>> cachedTokens: " + details.cachedTokens());

                if (details.cachedTokens() != null && details.cachedTokens() > 0) {
                    double hitRate = (double) details.cachedTokens() / tokenUsage.inputTokenCount() * 100;
                    System.out.println(">>> ✅ 缓存命中! 命中率: " + String.format("%.1f%%", hitRate));
                } else {
                    System.out.println(">>> ❌ 无缓存命中 (cachedTokens=" + details.cachedTokens() + ")");
                }
            } else {
                System.out.println(">>> ❌ inputTokensDetails 为 null (智谱AI可能未返回 prompt_tokens_details)");
            }
        } else {
            System.out.println(">>> 不是 OpenAiTokenUsage，实际类型: " + tokenUsage.getClass().getName());

            // 反射尝试
            try {
                var detailsMethod = tokenUsage.getClass().getMethod("inputTokensDetails");
                Object details = detailsMethod.invoke(tokenUsage);
                System.out.println(">>> inputTokensDetails(): " + details);

                if (details != null) {
                    var cachedMethod = details.getClass().getMethod("cachedTokens");
                    Object cached = cachedMethod.invoke(details);
                    System.out.println(">>> cachedTokens(): " + cached);
                }
            } catch (NoSuchMethodException e) {
                System.out.println(">>> 该 TokenUsage 类型没有 inputTokensDetails() 方法");
            } catch (Exception e) {
                System.out.println(">>> 反射提取失败: " + e.getMessage());
            }
        }
    }
}
