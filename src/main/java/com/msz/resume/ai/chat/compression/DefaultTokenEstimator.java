package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认 Token 估算器实现
 *
 * <p>使用锚点法估算 Token 数量，提高估算精度。
 *
 * <p>估算策略：
 * <ul>
 *   <li>有锚点：使用锚点 + 增量估算（精度 ~5%）</li>
 *   <li>无锚点：使用字符数估算（精度 ~30%）</li>
 * </ul>
 *
 * <p>字符到 Token 的转换系数：
 * <ul>
 *   <li>英文：约 4 字符 = 1 Token</li>
 *   <li>中文：约 1.5 字符 = 1 Token</li>
 *   <li>混合：使用保守估计，字符数 / 3</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultTokenEstimator implements TokenEstimator {

    /** 字符到 Token 的转换系数（保守估计） */
    private static final double CHARS_PER_TOKEN = 3.0;

    /** 锚点 Token 数 */
    private volatile int anchor = 0;

    /** 锚点对应的消息数量 */
    private volatile int anchorMessageCount = 0;

    @Override
    public int estimate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        if (anchor == 0 || anchorMessageCount == 0) {
            // 无锚点，使用全量估算
            return estimateFromMessages(messages);
        }

        // 有锚点，计算增量
        int currentCount = messages.size();
        if (currentCount <= anchorMessageCount) {
            // 消息数量未增加或减少，使用全量估算
            return estimateFromMessages(messages);
        }

        // 计算新增消息的 Token 数
        List<ChatMessage> newMessages = messages.subList(anchorMessageCount, currentCount);
        int incrementalTokens = estimateFromMessages(newMessages);

        return anchor + incrementalTokens;
    }

    @Override
    public void updateAnchor(int promptTokens) {
        this.anchor = promptTokens;
        log.debug("[TokenEstimator] 更新锚点: {} tokens", promptTokens);
    }

    @Override
    public void updateAnchor(int promptTokens, int messageCount) {
        this.anchor = promptTokens;
        this.anchorMessageCount = messageCount;
        log.debug("[TokenEstimator] 更新锚点: {} tokens, {} 条消息", promptTokens, messageCount);
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
        this.anchor = 0;
        this.anchorMessageCount = 0;
        log.debug("[TokenEstimator] 重置锚点");
    }

    /**
     * 从消息列表估算 Token 数
     *
     * <p>遍历所有消息，累加字符数，然后转换为 Token 数。
     *
     * @param messages 消息列表
     * @return 估算的 Token 数
     */
    private int estimateFromMessages(List<ChatMessage> messages) {
        int totalChars = 0;
        for (ChatMessage message : messages) {
            totalChars += estimateMessageChars(message);
        }
        return (int) Math.ceil(totalChars / CHARS_PER_TOKEN);
    }

    /**
     * 估算单条消息的字符数
     *
     * <p>根据消息类型提取文本内容，计算字符数。
     *
     * @param message 消息
     * @return 字符数
     */
    private int estimateMessageChars(ChatMessage message) {
        // LangChain4j 消息类型处理
        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length();
    }

    /**
     * 从消息中提取文本内容
     *
     * @param message 消息
     * @return 文本内容
     */
    private String extractText(ChatMessage message) {
        String extracted = ChatMessageTextExtractor.extract(message);
        if (extracted != null && !extracted.isEmpty()) {
            return extracted;
        }

        // 不同消息类型有不同的文本提取方式
        try {
            // 尝试调用 text() 方法（适用于 ToolExecutionResultMessage, AiMessage 等）
            var textMethod = message.getClass().getMethod("text");
            Object result = textMethod.invoke(message);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (NoSuchMethodException e) {
            // text() 方法不存在，尝试其他方法
        } catch (Exception e) {
            // 其他异常，继续尝试其他方法
        }

        try {
            // 尝试调用 singleText() 方法（适用于 UserMessage, SystemMessage 等）
            var singleTextMethod = message.getClass().getMethod("singleText");
            Object result = singleTextMethod.invoke(message);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Exception e) {
            // singleText() 方法不存在或调用失败
        }

        // 回退：返回空字符串
        return "";
    }
}
