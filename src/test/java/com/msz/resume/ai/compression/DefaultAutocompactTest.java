package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import com.msz.resume.ai.chat.compression.model.SplitResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultAutocompact 单元测试
 *
 * <p>使用测试辅助类替代 Mockito
 */
class DefaultAutocompactTest {

    private AutocompactConfig config;
    private TestableTokenEstimator tokenEstimator;
    private TestableChatModel chatModel;
    private TestableMessageSplitCalculator splitCalculator;
    private TestablePostCompactRestorer restorer;
    private DefaultAutocompact autocompact;

    @BeforeEach
    void setUp() {
        config = new AutocompactConfig();
        config.setContextWindow(100000);  // 100K 窗口
        config.setReservedOutputTokens(20000);
        config.setThresholdOffset(13000);
        config.setMaxConsecutiveFailures(3);

        tokenEstimator = new TestableTokenEstimator();
        chatModel = new TestableChatModel();
        splitCalculator = new TestableMessageSplitCalculator();
        restorer = new TestablePostCompactRestorer();

        autocompact = new DefaultAutocompact(config, tokenEstimator, chatModel, splitCalculator, restorer);
    }

    @Test
    @DisplayName("消息太少时跳过压缩")
    void compact_whenTooFewMessages_shouldSkip() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        tokenEstimator.setEstimate(100);
        splitCalculator.setShouldSplit(false);

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertTrue(result.success());
        assertEquals(100, result.originalTokens());
        assertEquals(100, result.compactedTokens());  // 未变化
    }

    @Test
    @DisplayName("Token 未达阈值时跳过压缩")
    void compact_whenBelowThreshold_shouldSkip() {
        List<ChatMessage> messages = createMessages(10);

        // 阈值计算: 100000 - 20000 (预留) - 13000 (偏移) = 67000
        tokenEstimator.setEstimate(50000);  // 50K，低于阈值

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertTrue(result.success());
        assertEquals(50000, result.originalTokens());
        assertEquals(50000, result.compactedTokens());  // 未变化
    }

    @Test
    @DisplayName("Token 超过阈值时执行压缩")
    void compact_whenAboveThreshold_shouldCompact() {
        List<ChatMessage> messages = createMessages(20);

        // 阈值计算: 100000 - 20000 - 13000 = 67000
        tokenEstimator.setEstimates(80000, 5000);  // 原始 80K，压缩后 5K

        // 设置分割结果
        splitCalculator.setSplitResult(SplitResult.of(10, 10, 30000));

        // Mock LLM 响应
        chatModel.setResponse(AiMessage.from("<analysis>思考过程</analysis>\n<summary>\n## 1. Primary Request\n用户请求...\n</summary>"));

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertTrue(result.success());
        assertEquals(80000, result.originalTokens());
        assertEquals(5000, result.compactedTokens());
        assertTrue(result.tokensSaved() > 0);

        // 验证调用了 LLM
        assertTrue(chatModel.wasChatCalled());
    }

    @Test
    @DisplayName("成功压缩时返回 checkpoint 所需 split metadata")
    void compact_whenSuccess_shouldReturnCheckpointMetadata() {
        List<ChatMessage> messages = createMessages(20);

        tokenEstimator.setEstimates(80000, 5000);
        splitCalculator.setSplitResult(SplitResult.of(10, 30000, 10));
        chatModel.setResponse(AiMessage.from("<summary>摘要内容</summary>"));

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertTrue(result.success());
        assertEquals(10, result.splitIndex());
        assertEquals(10, result.preservedCount());
        assertEquals(1, result.summaryPrefixMessages().size());
        assertEquals(result.summaryPrefixMessages().getFirst(), result.messages().getFirst());
        assertEquals(messages.get(10), result.messages().get(1));
    }

    @Test
    @DisplayName("熔断器触发时跳过压缩")
    void compact_whenCircuitBreakerTripped_shouldSkip() {
        List<ChatMessage> messages = createMessages(20);

        tokenEstimator.setEstimate(80000);
        splitCalculator.setSplitResult(SplitResult.of(10, 10, 30000));

        // 模拟连续失败 3 次
        chatModel.setThrowException(true);
        autocompact.compact(messages, "session1");  // 失败 1
        autocompact.compact(messages, "session1");  // 失败 2
        autocompact.compact(messages, "session1");  // 失败 3，熔断器触发

        // 再调用应该跳过
        AutocompactResult result = autocompact.compact(messages, "session1");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("熔断器"));
    }

    @Test
    @DisplayName("needsCompact 正确判断是否需要压缩")
    void needsCompact_shouldReturnCorrectResult() {
        // 阈值 = 100000 - 20000 - 13000 = 67000
        assertFalse(autocompact.needsCompact(50000));  // 50K < 67K
        assertFalse(autocompact.needsCompact(66999));  // 66.999K < 67K
        assertTrue(autocompact.needsCompact(67000));   // 67K >= 67K
        assertTrue(autocompact.needsCompact(80000));   // 80K >= 67K
    }

    @Test
    @DisplayName("成功压缩后重置熔断器")
    void compact_whenSuccess_shouldResetCircuitBreaker() {
        List<ChatMessage> messages = createMessages(20);

        tokenEstimator.setEstimates(80000, 5000);
        splitCalculator.setSplitResult(SplitResult.of(10, 10, 30000));

        chatModel.setResponse(AiMessage.from("<summary>摘要内容</summary>"));

        autocompact.compact(messages, "session1");
        autocompact.resetCircuitBreaker("session1");

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertEquals(0, autocompact.getConsecutiveFailures("session1"));
    }

    @Test
    @DisplayName("提取 summary 内容")
    void extractSummary_shouldExtractContent() {
        List<ChatMessage> messages = createMessages(20);

        tokenEstimator.setEstimates(80000, 5000);
        splitCalculator.setSplitResult(SplitResult.of(10, 10, 30000));

        chatModel.setResponse(AiMessage.from(
                "<analysis>这是分析内容，应该被剥离</analysis>\n\n" +
                "<summary>\n## 1. Primary Request\n用户请求内容\n\n## 2. Key Concepts\n关键概念\n</summary>"
        ));

        AutocompactResult result = autocompact.compact(messages, "session1");

        assertTrue(result.success());
        // 验证消息内容不包含 analysis
        String summaryContent = result.messages().get(0).toString();
        assertFalse(summaryContent.contains("<analysis>"));
    }

    @Test
    @DisplayName("null 消息列表返回成功")
    void compact_whenNull_shouldReturnSuccess() {
        AutocompactResult result = autocompact.compact(null, "session1");
        assertTrue(result.success());
    }

    @Test
    @DisplayName("重置熔断器")
    void resetCircuitBreaker_shouldReset() {
        // 先设置一些失败
        autocompact.resetCircuitBreaker("session1");
        assertEquals(0, autocompact.getConsecutiveFailures("session1"));
    }

    // ==================== 辅助方法 ====================

    private List<ChatMessage> createMessages(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(UserMessage.from("Message content " + i + " with some additional text to make it longer"));
        }
        return messages;
    }

    // ==================== 测试辅助类 ====================

    static class TestableTokenEstimator implements TokenEstimator {
        private int estimate = 0;
        private int estimateIndex = 0;
        private int[] estimates = new int[1];
        private int anchor = 0;
        private int anchorMessageCount = 0;

        public void setEstimate(int estimate) {
            this.estimate = estimate;
            this.estimates = new int[]{estimate};
            this.estimateIndex = 0;
        }

        public void setEstimates(int... estimates) {
            this.estimates = estimates;
            this.estimateIndex = 0;
        }

        @Override
        public int estimate(List<ChatMessage> messages) {
            if (estimateIndex < estimates.length) {
                return estimates[estimateIndex++];
            }
            return estimate;
        }

        @Override
        public void updateAnchor(int promptTokens) {
            this.anchor = promptTokens;
        }

        @Override
        public void updateAnchor(int promptTokens, int messageCount) {
            this.anchor = promptTokens;
            this.anchorMessageCount = messageCount;
        }

        @Override
        public int getAnchor() {
            return anchor;
        }

        @Override
        public int getAnchorMessageCount() {
            return anchorMessageCount;
        }

        @Override
        public void reset() {
            anchor = 0;
            anchorMessageCount = 0;
            estimateIndex = 0;
        }
    }

    static class TestableChatModel implements ChatModel {
        private AiMessage response;
        private boolean chatCalled = false;
        private boolean throwException = false;

        public void setResponse(AiMessage response) {
            this.response = response;
        }

        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }

        public boolean wasChatCalled() {
            return chatCalled;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            chatCalled = true;
            if (throwException) {
                throw new RuntimeException("Test exception");
            }

            ChatResponse mockResponse = new ChatResponse.Builder()
                    .aiMessage(response != null ? response : AiMessage.from("Default response"))
                    .build();

            return mockResponse;
        }
    }

    static class TestableMessageSplitCalculator extends MessageSplitCalculator {
        private SplitResult splitResult = SplitResult.noSplit();

        public TestableMessageSplitCalculator() {
            super(null, null);
        }

        public void setSplitResult(SplitResult result) {
            this.splitResult = result;
        }

        public void setShouldSplit(boolean shouldSplit) {
            if (shouldSplit) {
                this.splitResult = SplitResult.of(5, 5, 10000);
            } else {
                this.splitResult = SplitResult.noSplit();
            }
        }

        @Override
        public SplitResult calculateSplitIndex(List<ChatMessage> messages) {
            return splitResult;
        }
    }

    static class TestablePostCompactRestorer extends PostCompactRestorer {
        public TestablePostCompactRestorer() {
            super(null, null);
        }

        @Override
        public List<ChatMessage> restore(String sessionId, List<ChatMessage> preservedMessages) {
            return new ArrayList<>();  // 返回空列表
        }
    }
}
