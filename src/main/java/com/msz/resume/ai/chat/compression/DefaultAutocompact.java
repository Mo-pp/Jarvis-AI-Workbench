package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import com.msz.resume.ai.chat.compression.model.SplitResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认自动压缩器实现
 *
 * <p>L5 Autocompact 完整实现，包含尾部保护机制：
 * <ul>
 *   <li>分割消息列表，保留最近 N 条消息</li>
 *   <li>保护 tool_use/tool_result 配对完整性</li>
 *   <li>只压缩前半部分，生成摘要</li>
 *   <li>压缩后恢复 Skill、Plan 等信息</li>
 *   <li>熔断器：连续失败 N 次后停止尝试</li>
 *   <li>PTL 重试：最多 N 次</li>
 * </ul>
 *
 * <p>流程图：
 * <pre>
 * 原始消息: [M0] [M1] [M2] [M3] [M4] [M5] [M6] [M7] [M8] [M9]
 *           │                              │                   │
 *           └──────── 压缩部分 ────────────┘└──── 保留部分 ─────┘
 *                                          splitIndex = 5
 *
 * 压缩结果:
 * [摘要消息] + [恢复的 Skill/Plan] + [保留的原始消息]
 * </pre>
 */
@Slf4j
@Component
public class DefaultAutocompact implements Autocompact {

    /** 熔断器状态：sessionId -> 连续失败次数 */
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    private final AutocompactConfig config;
    private final TokenEstimator tokenEstimator;
    private final ChatModel chatModel;
    private final MessageSplitCalculator splitCalculator;
    private final PostCompactRestorer restorer;

    public DefaultAutocompact(AutocompactConfig config,
                              TokenEstimator tokenEstimator,
                              ChatModel chatModel,
                              MessageSplitCalculator splitCalculator,
                              PostCompactRestorer restorer) {
        this.config = config;
        this.tokenEstimator = tokenEstimator;
        this.chatModel = chatModel;
        this.splitCalculator = splitCalculator;
        this.restorer = restorer;
    }

    @Override
    public AutocompactResult compact(List<ChatMessage> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return AutocompactResult.skipped(messages, 0);
        }

        // 1. 检查熔断器
        if (isCircuitBreakerTripped(sessionId)) {
            log.warn("[Autocompact] 熔断器已触发，跳过压缩: sessionId={}", sessionId);
            return AutocompactResult.failure(tokenEstimator.estimate(messages), "熔断器已触发");
        }

        int totalTokens = tokenEstimator.estimate(messages);

        // 2. 检查是否需要压缩
        if (!needsCompact(totalTokens)) {
            log.debug("[Autocompact] 未达触发阈值，跳过压缩: tokens={}", totalTokens);
            return AutocompactResult.skipped(messages, totalTokens);
        }

        log.info("[Autocompact] 开始压缩: sessionId={}, totalTokens={}", sessionId, totalTokens);

        try {
            // 3. 计算分割点（尾部保护）
            SplitResult split = splitCalculator.calculateSplitIndex(messages);

            if (!split.shouldSplit()) {
                log.info("[Autocompact] 消息数不足，无需分割");
                return AutocompactResult.skipped(messages, totalTokens);
            }

            log.info("[Autocompact] 分割点: {}, 保留 {} 条消息, {} tokens",
                    split.splitIndex(), split.preservedCount(), split.preservedTokens());

            // 4. 分割消息
            List<ChatMessage> toCompress = messages.subList(0, split.splitIndex());
            List<ChatMessage> toPreserve = new ArrayList<>(messages.subList(split.splitIndex(), messages.size()));

            // 5. 压缩前半部分
            String summary = compressMessages(toCompress, sessionId);

            // 6. 构建摘要消息
            ChatMessage summaryMessage = UserMessage.from(buildSummaryMessage(summary, toCompress.size()));

            // 7. 压缩后恢复（Skill、Plan）
            List<ChatMessage> restored = restorer.restore(sessionId, toPreserve);

            // 8. 合并结果
            List<ChatMessage> result = new ArrayList<>();
            result.add(summaryMessage);
            result.addAll(restored);
            result.addAll(toPreserve);

            int resultTokens = tokenEstimator.estimate(result);

            // 9. 重置熔断器
            resetCircuitBreaker(sessionId);

            log.info("[Autocompact] 压缩完成: {} → {} tokens, 保留 {} 条消息, 恢复 {} 条附加消息",
                    totalTokens, resultTokens, toPreserve.size(), restored.size());

            return AutocompactResult.success(result, totalTokens, resultTokens);

        } catch (Exception e) {
            log.error("[Autocompact] 压缩失败: {}", e.getMessage(), e);

            // 递增熔断器计数
            int failures = consecutiveFailures.merge(sessionId, 1, Integer::sum);
            if (failures >= config.getMaxConsecutiveFailures()) {
                log.warn("[Autocompact] 熔断器触发: sessionId={}, consecutiveFailures={}", sessionId, failures);
            }

            return AutocompactResult.failure(totalTokens, e.getMessage());
        }
    }

    /**
     * 压缩消息，生成摘要
     *
     * @param messages  要压缩的消息列表
     * @param sessionId 会话 ID
     * @return 摘要内容
     */
    private String compressMessages(List<ChatMessage> messages, String sessionId) {
        String prompt = buildCompactPrompt(messages);

        // 带重试的 LLM 调用
        int retries = 0;
        int maxRetries = config.getMaxPtlRetries();

        while (retries < maxRetries) {
            try {
                ChatRequest request = ChatRequest.builder()
                        .messages(UserMessage.from(prompt))
                        .build();
                ChatResponse response = chatModel.chat(request);

                if (response == null || response.aiMessage() == null) {
                    throw new RuntimeException("LLM 返回空响应");
                }

                String content = response.aiMessage().text();
                return extractSummary(content);

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("prompt is too long")) {
                    retries++;
                    log.warn("[Autocompact] PTL 错误，重试 {}/{}", retries, maxRetries);
                    prompt = truncatePrompt(prompt);
                } else {
                    throw e;
                }
            }
        }

        throw new RuntimeException("PTL 重试次数耗尽");
    }

    @Override
    public boolean needsCompact(int tokens) {
        int threshold = config.getTriggerThreshold();
        return tokens >= threshold;
    }

    @Override
    public boolean isCircuitBreakerTripped(String sessionId) {
        return consecutiveFailures.getOrDefault(sessionId, 0) >= config.getMaxConsecutiveFailures();
    }

    @Override
    public void resetCircuitBreaker(String sessionId) {
        consecutiveFailures.remove(sessionId);
    }

    @Override
    public int getConsecutiveFailures(String sessionId) {
        return consecutiveFailures.getOrDefault(sessionId, 0);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建压缩 Prompt
     */
    private String buildCompactPrompt(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();

        sb.append("CRITICAL: 只回复文本，不要调用任何工具。\n");
        sb.append("工具调用将被拒绝并浪费你的唯一机会。\n\n");

        sb.append("请将以下对话历史压缩为一份精简摘要。\n");
        sb.append("对话历史:\n\n");

        for (ChatMessage msg : messages) {
            sb.append(formatMessage(msg)).append("\n\n");
        }

        sb.append("\n请按照以下格式输出摘要:\n\n");

        sb.append("<analysis>\n");
        sb.append("[在这里进行链式思考推理，分析整个对话]\n");
        sb.append("</analysis>\n\n");

        sb.append("<summary>\n");
        sb.append("## 1. Primary Request and Intent\n");
        sb.append("用户的原始需求和意图\n\n");

        sb.append("## 2. Key Technical Concepts\n");
        sb.append("涉及的技术概念和架构决策\n\n");

        sb.append("## 3. Files and Code Sections\n");
        sb.append("重要的文件路径和关键代码片段\n\n");

        sb.append("## 4. Errors and Fixes\n");
        sb.append("遇到的错误和修复方案\n\n");

        sb.append("## 5. All User Messages\n");
        sb.append("用户的所有消息（逐条列出）\n\n");

        sb.append("## 6. Pending Tasks\n");
        sb.append("待完成的任务\n\n");

        sb.append("## 7. Current Work\n");
        sb.append("当前工作的详细状态\n\n");

        sb.append("## 8. Optional Next Step\n");
        sb.append("下一步建议\n");
        sb.append("</summary>\n");

        return sb.toString();
    }

    /**
     * 格式化消息
     */
    private String formatMessage(ChatMessage msg) {
        String role = msg.getClass().getSimpleName().replace("Message", "");
        return "[" + role + "]: " + msg.toString();
    }

    /**
     * 截断 Prompt（PTL 恢复）
     */
    private String truncatePrompt(String prompt) {
        // 简单实现：移除前 20% 的内容
        int truncatePoint = (int) (prompt.length() * 0.8);
        return "[更早的对话已截断以重试压缩]\n\n" + prompt.substring(prompt.length() - truncatePoint);
    }

    /**
     * 提取 <summary> 部分
     */
    private String extractSummary(String content) {
        if (content == null) {
            return "";
        }

        int summaryStart = content.indexOf("<summary>");
        int summaryEnd = content.indexOf("</summary>");

        if (summaryStart >= 0 && summaryEnd > summaryStart) {
            return content.substring(summaryStart + "<summary>".length(), summaryEnd).trim();
        }

        // 没有找到 <summary> 标签，返回原始内容
        return content;
    }

    /**
     * 构建摘要消息
     */
    private String buildSummaryMessage(String summary, int originalMessageCount) {
        StringBuilder sb = new StringBuilder();

        sb.append("[对话历史摘要]\n\n");
        sb.append("原始对话包含 ").append(originalMessageCount).append(" 条消息。\n\n");
        sb.append(summary);

        return sb.toString();
    }
}
