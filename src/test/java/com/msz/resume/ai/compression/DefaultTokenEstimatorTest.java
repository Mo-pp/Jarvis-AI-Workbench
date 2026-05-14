package com.msz.resume.ai.chat.compression;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultTokenEstimator 单元测试
 */
class DefaultTokenEstimatorTest {

    private DefaultTokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new DefaultTokenEstimator();
    }

    @Test
    @DisplayName("estimate() 空消息列表返回 0")
    void estimate_whenEmptyMessages_shouldReturnZero() {
        assertEquals(0, estimator.estimate(new ArrayList<>()));
        assertEquals(0, estimator.estimate(null));
    }

    @Test
    @DisplayName("estimate() 无锚点时使用字符数估算")
    void estimate_whenNoAnchor_shouldUseCharacterBasedEstimation() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello World")); // 11 字符 ≈ 4 tokens

        int tokens = estimator.estimate(messages);

        assertTrue(tokens > 0);
        // 11 字符 / 3 ≈ 4 tokens (允许一定误差)
        assertTrue(tokens >= 3 && tokens <= 6, "Expected ~4 tokens, got " + tokens);
    }

    @Test
    @DisplayName("updateAnchor() 正确更新锚点值")
    void updateAnchor_shouldUpdateAnchorValue() {
        assertEquals(0, estimator.getAnchor());

        estimator.updateAnchor(1000);
        assertEquals(1000, estimator.getAnchor());

        estimator.updateAnchor(2000, 5);
        assertEquals(2000, estimator.getAnchor());
        assertEquals(5, estimator.getAnchorMessageCount());
    }

    @Test
    @DisplayName("estimate() 有锚点时使用增量估算")
    void estimate_whenHasAnchor_shouldUseIncrementalEstimation() {
        // 设置锚点：1000 tokens 对应 2 条消息
        estimator.updateAnchor(1000, 2);

        // 添加 3 条消息（锚点 2 + 新增 1）
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Message 1"));
        messages.add(UserMessage.from("Message 2"));
        messages.add(UserMessage.from("New message 3")); // 新增的消息

        int tokens = estimator.estimate(messages);

        // 应该是锚点 + 新增消息的估算
        // 新增消息 "New message 3" ≈ 13 字符 ≈ 5 tokens
        // 总计 ≈ 1000 + 5 = 1005 tokens (允许一定误差)
        assertTrue(tokens >= 1003 && tokens <= 1008, "Expected ~1005 tokens, got " + tokens);
    }

    @Test
    @DisplayName("estimate() 消息数量未增加时使用全量估算")
    void estimate_whenMessagesNotIncreased_shouldUseFullEstimation() {
        // 设置锚点：1000 tokens 对应 3 条消息
        estimator.updateAnchor(1000, 3);

        // 消息数量未增加
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Message 1"));
        messages.add(UserMessage.from("Message 2"));
        messages.add(UserMessage.from("Message 3"));

        int tokens = estimator.estimate(messages);

        // 消息数量未增加，使用全量估算
        // 3 条消息 ≈ 27 字符 ≈ 9 tokens
        assertTrue(tokens > 0);
        assertTrue(tokens < 15, "Should use character-based estimation, got " + tokens);
    }

    @Test
    @DisplayName("reset() 清除锚点")
    void reset_shouldClearAnchor() {
        estimator.updateAnchor(1000, 5);
        assertEquals(1000, estimator.getAnchor());
        assertEquals(5, estimator.getAnchorMessageCount());

        estimator.reset();

        assertEquals(0, estimator.getAnchor());
        assertEquals(0, estimator.getAnchorMessageCount());
    }

    @Test
    @DisplayName("长文本估算合理")
    void estimate_shouldHandleLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is a test message. ");
        }
        String longText = sb.toString();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(longText));

        int tokens = estimator.estimate(messages);

        // 约 2600 字符 ≈ 867 tokens (允许一定误差)
        assertTrue(tokens > 750 && tokens < 950, "Expected ~867 tokens, got " + tokens);
    }

    @Test
    @DisplayName("中文文本估算合理")
    void estimate_shouldHandleChineseText() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("这是一段中文测试文本，用于验证中文 Token 估算的准确性。"));

        int tokens = estimator.estimate(messages);

        // 约 30 字符 / 3 ≈ 10 tokens (允许一定误差)
        assertTrue(tokens > 0);
        assertTrue(tokens >= 8 && tokens <= 20, "Expected ~10 tokens, got " + tokens);
    }
}
