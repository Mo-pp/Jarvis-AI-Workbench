package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.SplitResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息分割计算器
 *
 * <p>计算 L5 Autocompact 的消息分割点，实现尾部保护：
 * <ul>
 *   <li>从后向前扩展，保留满足最小要求的消息</li>
 *   <li>保护 tool_use/tool_result 配对完整性</li>
 *   <li>遵守 token 预算限制</li>
 * </ul>
 *
 * <p>分割规则：
 * <pre>
 * 原始消息: [M0] [M1] [M2] [M3] [M4] [M5] [M6] [M7] [M8] [M9]
 *           │                              │                   │
 *           └──────── 压缩部分 ────────────┘└──── 保留部分 ─────┘
 *                                          splitIndex = 5
 * </pre>
 */
@Slf4j
@Component
public class MessageSplitCalculator {

    private final TokenEstimator tokenEstimator;
    private final AutocompactConfig config;

    public MessageSplitCalculator(TokenEstimator tokenEstimator, AutocompactConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config;
    }

    /**
     * 计算分割索引
     *
     * <p>从后向前遍历消息列表，找到满足以下条件的最小分割点：
     * <ol>
     *   <li>保留部分 token 数 >= minTokensToPreserve</li>
     *   <li>保留部分消息数 >= minMessagesToKeep</li>
     *   <li>保留部分 token 数 <= maxTokensToPreserve</li>
     *   <li>不破坏 tool_use/tool_result 配对</li>
     * </ol>
     *
     * @param messages 消息列表
     * @return 分割结果
     */
    public SplitResult calculateSplitIndex(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("[SplitCalculator] 消息列表为空，不分割");
            return SplitResult.noSplit();
        }

        int n = messages.size();
        int minMessagesToKeep = config.getMinMessagesToKeep();

        // 消息太少，不分割
        if (n <= minMessagesToKeep) {
            log.debug("[SplitCalculator] 消息数 {} <= 最小保留数 {}，不分割", n, minMessagesToKeep);
            return SplitResult.noSplit();
        }

        // 1. 从后向前扩展，找到满足最小要求的分割点
        int splitIndex = n;
        int preservedTokens = 0;
        int preservedCount = 0;

        int minTokensToPreserve = config.getMinTokensToPreserve();
        int maxTokensToPreserve = config.getMaxTokensToPreserve();

        for (int i = n - 1; i >= 0; i--) {
            int msgTokens = tokenEstimator.estimate(List.of(messages.get(i)));

            // 检查是否超过最大上限
            if (preservedTokens + msgTokens > maxTokensToPreserve) {
                log.debug("[SplitCalculator] 达到最大 token 上限 {}，停止扩展", maxTokensToPreserve);
                break;
            }

            preservedTokens += msgTokens;
            preservedCount++;
            splitIndex = i;

            // 检查是否满足最小要求
            if (preservedTokens >= minTokensToPreserve && preservedCount >= minMessagesToKeep) {
                log.debug("[SplitCalculator] 满足最小要求: tokens={}, count={}", preservedTokens, preservedCount);
                break;
            }
        }

        // 所有消息都要保留（不满足最小要求时可能已经遍历完）
        if (splitIndex == 0) {
            log.debug("[SplitCalculator] 所有消息都需要保留，不分割");
            return SplitResult.noSplit();
        }

        // 2. 调整分割点，保护 tool_use/tool_result 配对
        int originalSplitIndex = splitIndex;
        splitIndex = adjustForToolPairs(messages, splitIndex);

        if (splitIndex != originalSplitIndex) {
            log.debug("[SplitCalculator] 调整分割点保护工具配对: {} → {}", originalSplitIndex, splitIndex);
            // 重新计算保留部分的 token 数
            preservedTokens = 0;
            preservedCount = 0;
            for (int i = splitIndex; i < n; i++) {
                preservedTokens += tokenEstimator.estimate(List.of(messages.get(i)));
                preservedCount++;
            }
        }

        log.info("[SplitCalculator] 分割点: {}, 保留 {} 条消息, {} tokens",
                splitIndex, preservedCount, preservedTokens);

        return SplitResult.of(splitIndex, preservedTokens, preservedCount);
    }

    /**
     * 调整分割点，确保不破坏 tool_use/tool_result 配对
     *
     * <p>规则：
     * <ul>
     *   <li>如果保留部分第一条是 ToolExecutionResultMessage，找到对应的 AiMessage</li>
     *   <li>如果保留部分第一条是带 toolExecutionRequests 的 AiMessage，检查前一条是否是配对的结果</li>
     * </ul>
     *
     * @param messages    消息列表
     * @param splitIndex  原始分割索引
     * @return 调整后的分割索引
     */
    private int adjustForToolPairs(List<ChatMessage> messages, int splitIndex) {
        if (splitIndex <= 0 || splitIndex >= messages.size()) {
            return splitIndex;
        }

        ChatMessage firstPreserved = messages.get(splitIndex);

        // 情况 1：保留部分第一条是 ToolExecutionResultMessage
        // 需要找到对应的 AiMessage (tool_use)
        if (firstPreserved instanceof ToolExecutionResultMessage toolResult) {
            String toolCallId = toolResult.id();

            // 向前查找匹配的 AiMessage
            for (int i = splitIndex - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                    boolean found = aiMsg.toolExecutionRequests().stream()
                            .anyMatch(req -> req.id().equals(toolCallId));
                    if (found) {
                        log.debug("[SplitCalculator] 工具结果 {} 对应的 tool_use 在索引 {}", toolCallId, i);
                        return i;  // 从 tool_use 开始保留
                    }
                }
            }

            // 没找到对应的 tool_use，这可能是一个孤立的结果
            // 为了安全起见，把它也加入保留部分
            log.warn("[SplitCalculator] 未找到工具结果 {} 对应的 tool_use，可能数据不完整", toolCallId);
        }

        // 情况 2：保留部分第一条是 AiMessage 且有 toolExecutionRequests
        // 检查压缩部分最后一条是否是对应的 ToolExecutionResultMessage
        if (firstPreserved instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
            String firstToolCallId = aiMsg.toolExecutionRequests().get(0).id();

            // 检查前一条是否是同一工具调用的结果
            if (splitIndex > 0) {
                ChatMessage lastCompressed = messages.get(splitIndex - 1);
                if (lastCompressed instanceof ToolExecutionResultMessage toolResult) {
                    if (toolResult.id().equals(firstToolCallId)) {
                        log.debug("[SplitCalculator] tool_use {} 的结果在前一条，调整分割点", firstToolCallId);
                        // 工具结果在压缩部分，需要一起保留
                        return splitIndex - 1;
                    }
                }
            }
        }

        return splitIndex;
    }
}
