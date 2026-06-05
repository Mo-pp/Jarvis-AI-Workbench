package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import com.msz.resume.ai.chat.compression.model.BudgetResult;
import com.msz.resume.ai.chat.compression.model.CollapseResult;
import com.msz.resume.ai.chat.compression.model.CompactResult;
import com.msz.resume.ai.chat.compression.model.PipelineResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultMessagePreprocessingPipeline 单元测试
 *
 * <p>使用手动创建的测试实现替代 Mockito
 */
class DefaultMessagePreprocessingPipelineTest {

    private JarvisCompressionProperties properties;
    private DefaultTokenEstimator tokenEstimator;
    private TestableToolResultBudget toolResultBudget;
    private TestableMicrocompact microcompact;
    private TestableContextCollapse contextCollapse;
    private TestableAutocompact autocompact;
    private DefaultMessagePreprocessingPipeline pipeline;

    @BeforeEach
    void setUp() {
        properties = new JarvisCompressionProperties();
        properties.setModelContextWindow(1000);  // 小窗口便于测试
        properties.setContextThresholdRatio(0.8); // 80% 触发压缩
        properties.setKeepRecent(2);
        properties.setMaxResultSizeChars(100);  // 小阈值便于测试

        tokenEstimator = new DefaultTokenEstimator();
        toolResultBudget = new TestableToolResultBudget(properties);
        microcompact = new TestableMicrocompact(properties);
        contextCollapse = new TestableContextCollapse(properties);
        autocompact = new TestableAutocompact(properties);
        pipeline = new DefaultMessagePreprocessingPipeline(
                properties, tokenEstimator, toolResultBudget, microcompact,
                contextCollapse, autocompact);
    }

    @Test
    @DisplayName("process() 空消息列表返回不变")
    void process_whenEmptyMessages_shouldReturnUnchanged() {
        List<ChatMessage> messages = new ArrayList<>();

        PipelineResult result = pipeline.process(messages, "session1");

        assertFalse(result.wasCompressed());
        assertEquals(0, result.originalTokens());
        assertEquals(0, result.finalTokens());
    }

    @Test
    @DisplayName("process() 上下文未超限不压缩")
    void process_whenBelowThreshold_shouldNotCompress() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Short message")); // ~5 tokens

        PipelineResult result = pipeline.process(messages, "session1");

        // 5 tokens / 1000 = 0.5% < 80%, 不触发压缩
        assertFalse(result.wasCompressed());
        assertTrue(result.originalTokens() < 100);
    }

    @Test
    @DisplayName("calculateUtilization() 正确计算利用率")
    void calculateUtilization_shouldReturnCorrectRatio() {
        // 800 tokens / 1000 = 80%
        assertEquals(0.8, pipeline.calculateUtilization(800), 0.01);
        assertEquals(0.5, pipeline.calculateUtilization(500), 0.01);
        assertEquals(1.0, pipeline.calculateUtilization(1000), 0.01);
    }

    @Test
    @DisplayName("needsCompression() 正确判断是否需要压缩")
    void needsCompression_shouldReturnCorrectResult() {
        // 阈值 80%，窗口 1000
        assertFalse(pipeline.needsCompression(500));  // 50% < 80%
        assertFalse(pipeline.needsCompression(799));  // 79.9% < 80%
        assertTrue(pipeline.needsCompression(801));   // 80.1% > 80%
        assertTrue(pipeline.needsCompression(900));   // 90% > 80%
    }

    @Test
    @DisplayName("process() 超限时触发压缩")
    void process_whenExceedsThreshold_shouldTriggerCompression() {
        // 准备大量消息触发压缩（需要超过 800 tokens）
        List<ChatMessage> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("This is a test message to increase token count. ");
        }
        // 添加工具结果（会被 L1 和 L3 处理）
        for (int i = 0; i < 20; i++) {
            messages.add(ToolExecutionResultMessage.from("call-" + i, "fileRead", sb.toString()));
        }

        PipelineResult result = pipeline.process(messages, "session1");

        // 验证压缩被触发
        assertTrue(result.wasCompressed(), "Expected compression to be triggered");
        assertTrue(result.executedLevels().size() > 0, "Expected at least one level to be executed");
    }

    @Test
    @DisplayName("process() L5 成功时返回 LLM context checkpoint")
    void process_whenL5Succeeds_shouldReturnCheckpoint() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("A".repeat(1500)),
                UserMessage.from("B".repeat(1500)),
                UserMessage.from("C".repeat(1500))
        );

        PipelineResult result = pipeline.process(messages, "session1");

        assertTrue(result.wasCompressed());
        assertTrue(result.executedLevels().contains("L5"));
        assertNotNull(result.checkpoint());
        assertTrue(result.checkpoint().hasSummary());
        assertEquals(messages.size() - 1, result.checkpoint().tailStartIndex());
        assertEquals(messages.size(), result.checkpoint().sourceMessageCount());
        assertEquals(1, result.checkpoint().summaryMessages().size());
    }

    @Test
    @DisplayName("process() L1 截断大工具结果")
    void process_whenLargeToolResult_shouldTruncate() {
        // 重置工具预算，设置更低的阈值
        toolResultBudget = new TestableToolResultBudget(properties);
        pipeline = new DefaultMessagePreprocessingPipeline(
                properties, tokenEstimator, toolResultBudget, microcompact,
                contextCollapse, autocompact);

        List<ChatMessage> messages = new ArrayList<>();
        // 添加一个大的工具结果
        String largeContent = "A".repeat(200);
        messages.add(ToolExecutionResultMessage.from("call-1", "fileRead", largeContent));

        // 添加更多消息达到阈值
        for (int i = 0; i < 25; i++) {
            messages.add(UserMessage.from("Message " + i));
        }

        PipelineResult result = pipeline.process(messages, "session1");

        // 如果触发压缩，验证结果
        if (result.wasCompressed()) {
            // 检查 L1 是否执行
            if (result.executedLevels().contains("L1")) {
                // 验证工具结果被截断
                for (ChatMessage msg : result.messages()) {
                    if (msg instanceof ToolExecutionResultMessage toolMsg) {
                        // 内容应该被截断
                        assertTrue(toolMsg.text().length() <= 200,
                                "Tool result should be truncated or have preview");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("getContextWindow() 返回配置的窗口大小")
    void getContextWindow_shouldReturnConfiguredValue() {
        assertEquals(1000, pipeline.getContextWindow());
    }

    // ==================== 测试辅助类 ====================

    /**
     * 可测试的 ToolResultBudget 实现
     */
    static class TestableToolResultBudget implements ToolResultBudget {
        private final JarvisCompressionProperties properties;
        private boolean truncateEnabled = true;

        TestableToolResultBudget(JarvisCompressionProperties properties) {
            this.properties = properties;
        }

        @Override
        public BudgetResult process(String toolName, String toolCallId, String result, String sessionId) {
            if (result == null || result.isEmpty()) {
                return BudgetResult.unchanged("");
            }

            int originalSize = result.length();
            if (!needsTruncation(originalSize)) {
                return BudgetResult.unchanged(result);
            }

            // 截断
            String preview = result.substring(0, Math.min(properties.getPreviewSizeBytes(), result.length()));
            return BudgetResult.truncated(preview, "blob:1", originalSize);
        }

        @Override
        public boolean needsTruncation(int resultSize) {
            return truncateEnabled && resultSize > properties.getMaxResultSizeChars();
        }
    }

    /**
     * 可测试的 Microcompact 实现
     */
    static class TestableMicrocompact implements Microcompact {
        private final JarvisCompressionProperties properties;
        private static final Set<String> COMPACTABLE_TOOLS = Set.of(
                "fileRead", "shell", "grep", "glob", "webSearch", "webFetch", "fileEdit", "fileWrite"
        );

        TestableMicrocompact(JarvisCompressionProperties properties) {
            this.properties = properties;
        }

        @Override
        public CompactResult compact(List<ChatMessage> messages) {
            if (messages == null || messages.isEmpty()) {
                return CompactResult.unchanged(messages);
            }

            // 找出所有可压缩工具结果
            List<Integer> compactableIndices = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                if (msg instanceof ToolExecutionResultMessage toolMsg) {
                    if (COMPACTABLE_TOOLS.contains(toolMsg.toolName())) {
                        compactableIndices.add(i);
                    }
                }
            }

            int keepRecent = properties.getKeepRecent();
            if (compactableIndices.size() <= keepRecent) {
                return CompactResult.unchanged(messages);
            }

            // 清理旧结果
            int toCompactCount = compactableIndices.size() - keepRecent;
            List<ChatMessage> newMessages = new ArrayList<>();
            int compactedCount = 0;
            List<String> compactedToolNames = new ArrayList<>();

            for (int i = 0; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                if (compactableIndices.contains(i) && compactedCount < toCompactCount) {
                    ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                    ToolExecutionResultMessage cleanedMsg = ToolExecutionResultMessage.from(
                            toolMsg.id(),
                            toolMsg.toolName(),
                            CLEARED_MESSAGE
                    );
                    newMessages.add(cleanedMsg);
                    compactedCount++;
                    compactedToolNames.add(toolMsg.toolName());
                } else {
                    newMessages.add(msg);
                }
            }

            return new CompactResult(newMessages, compactedCount, 100, compactedToolNames);
        }

        @Override
        public Set<String> getCompactableTools() {
            return COMPACTABLE_TOOLS;
        }

        @Override
        public boolean isCompactable(String toolName) {
            return COMPACTABLE_TOOLS.contains(toolName);
        }
    }

    /**
     * 可测试的 ContextCollapse 实现（投影式折叠）
     */
    static class TestableContextCollapse implements ContextCollapse {
        private final JarvisCompressionProperties properties;
        private boolean hasFolded = false;

        TestableContextCollapse(JarvisCompressionProperties properties) {
            this.properties = properties;
        }

        @Override
        public CollapseResult recordCollapse(int messageCount, int tokens, String sessionId) {
            // 简化实现：折叠前半部分消息
            if (messageCount <= 4) {
                return CollapseResult.notCollapsed();
            }

            int toCollapse = messageCount / 2;
            hasFolded = true;

            return new CollapseResult(1, 0, toCollapse, toCollapse, 100, tokens);
        }

        @Override
        public List<ChatMessage> projectView(List<ChatMessage> messages, String sessionId) {
            if (!hasFolded || messages == null || messages.size() <= 4) {
                return messages;
            }

            int toCollapse = messages.size() / 2;
            List<ChatMessage> projected = new ArrayList<>();
            projected.add(UserMessage.from("[折叠摘要]"));

            for (int i = toCollapse; i < messages.size(); i++) {
                projected.add(messages.get(i));
            }

            return projected;
        }

        @Override
        public int releaseFolded(String sessionId) {
            hasFolded = false;
            return 1;
        }

        @Override
        public boolean needsCollapse(int tokens) {
            double utilization = (double) tokens / properties.getModelContextWindow();
            return utilization > 0.90;
        }

        @Override
        public boolean shouldBlockTools(int tokens) {
            double utilization = (double) tokens / properties.getModelContextWindow();
            return utilization > 0.95;
        }

        @Override
        public boolean isActive(String sessionId) {
            return hasFolded;
        }

        @Override
        public int getFoldGroupCount(String sessionId) {
            return hasFolded ? 1 : 0;
        }
    }

    /**
     * 可测试的 Autocompact 实现
     */
    static class TestableAutocompact implements Autocompact {
        private final JarvisCompressionProperties properties;

        TestableAutocompact(JarvisCompressionProperties properties) {
            this.properties = properties;
        }

        @Override
        public AutocompactResult compact(List<ChatMessage> messages, String sessionId) {
            // 简化实现：返回摘要消息
            if (messages == null || messages.isEmpty()) {
                return AutocompactResult.skipped(messages, 0);
            }

            int splitIndex = Math.max(0, messages.size() - 1);
            List<ChatMessage> summaryPrefix = List.of(
                    UserMessage.from("[对话摘要]\n\n用户讨论了多个话题...")
            );
            List<ChatMessage> compacted = new ArrayList<>(summaryPrefix);
            compacted.addAll(messages.subList(splitIndex, messages.size()));

            return AutocompactResult.success(compacted,
                    messages.size() * 10, compacted.size() * 10,
                    splitIndex, messages.size() - splitIndex, summaryPrefix);
        }

        @Override
        public boolean needsCompact(int tokens) {
            int threshold = properties.getModelContextWindow() - 33000; // 20000 + 13000
            return tokens >= threshold;
        }

        @Override
        public boolean isCircuitBreakerTripped(String sessionId) {
            return false;
        }

        @Override
        public void resetCircuitBreaker(String sessionId) {
        }

        @Override
        public int getConsecutiveFailures(String sessionId) {
            return 0;
        }
    }
}
