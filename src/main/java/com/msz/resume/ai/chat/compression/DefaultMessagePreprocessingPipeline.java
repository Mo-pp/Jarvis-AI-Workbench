package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import com.msz.resume.ai.chat.compression.model.BudgetResult;
import com.msz.resume.ai.chat.compression.model.CollapseResult;
import com.msz.resume.ai.chat.compression.model.CompactResult;
import com.msz.resume.ai.chat.compression.model.PipelineResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认消息预处理管线实现
 *
 * <p>实现 L1 → L3 → L5 压缩管线（L4 为 stub 状态）。
 *
 * <p>L1 Tool Result Budget：
 * <ul>
 *   <li>检查所有工具结果是否超过阈值</li>
 *   <li>超限结果持久化到数据库，返回预览</li>
 * </ul>
 *
 * <p>L3 Microcompact：
 * <ul>
 *   <li>清理过时的可压缩工具输出</li>
 *   <li>保留最近 keepRecent 个</li>
 * </ul>
 *
 * <p>L4 Context Collapse（⚠️ Stub 状态，暂未实现）：
 * <ul>
 *   <li>Claude Code 尚未实现此层级</li>
 *   <li>结构化剪裁/投影折叠功能预留</li>
 * </ul>
 *
 * <p>L5 Autocompact：
 * <ul>
 *   <li>调用 LLM 生成全量摘要</li>
 *   <li>作为最终兜底手段</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultMessagePreprocessingPipeline implements MessagePreprocessingPipeline {

    private final JarvisCompressionProperties properties;
    private final TokenEstimator tokenEstimator;
    private final ToolResultBudget toolResultBudget;
    private final Microcompact microcompact;
    private final ContextCollapse contextCollapse;
    private final Autocompact autocompact;

    public DefaultMessagePreprocessingPipeline(
            JarvisCompressionProperties properties,
            TokenEstimator tokenEstimator,
            ToolResultBudget toolResultBudget,
            Microcompact microcompact,
            ContextCollapse contextCollapse,
            Autocompact autocompact) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
        this.toolResultBudget = toolResultBudget;
        this.microcompact = microcompact;
        this.contextCollapse = contextCollapse;
        this.autocompact = autocompact;
    }

    @Override
    public PipelineResult process(List<ChatMessage> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return PipelineResult.unchanged(messages, 0);
        }

        // 1. 估算原始 Token 数
        int originalTokens = tokenEstimator.estimate(messages);

        // 2. 检查是否需要压缩
        if (!needsCompression(originalTokens)) {
            log.debug("[Pipeline] 上下文利用率 {}%，无需压缩",
                    String.format("%.1f", calculateUtilization(originalTokens) * 100));
            return PipelineResult.unchanged(messages, originalTokens);
        }

        log.info("[Pipeline] 上下文利用率 {}%，启动压缩管线",
                String.format("%.1f", calculateUtilization(originalTokens) * 100));

        List<ChatMessage> currentMessages = messages;
        List<String> executedLevels = new ArrayList<>();// 执行层级列表
        int currentTokens = originalTokens;

        // 3. L1: Tool Result Budget
        PipelineResult l1Result = applyL1(currentMessages, sessionId);
        if (l1Result != null) {
            currentMessages = l1Result.messages();
            executedLevels.add("L1");
            currentTokens = tokenEstimator.estimate(currentMessages);
            log.info("[Pipeline] L1 完成，tokens: {} → {}", originalTokens, currentTokens);

            // 检查是否足够
            if (!needsCompression(currentTokens)) {
                return new PipelineResult(currentMessages, true, originalTokens, currentTokens, executedLevels);
            }
        }

        // 4. L3: Microcompact
        CompactResult l3Result = microcompact.compact(currentMessages);
        if (l3Result.wasCompacted()) {
            currentMessages = l3Result.messages();
            executedLevels.add("L3");
            currentTokens = tokenEstimator.estimate(currentMessages);
            log.info("[Pipeline] L3 完成，清理 {} 个工具结果，tokens: {}",
                    l3Result.compactedCount(), currentTokens);

            // 检查是否足够
            if (!needsCompression(currentTokens)) {
                return new PipelineResult(currentMessages, true, originalTokens, currentTokens, executedLevels);
            }
        }

        // ========== L4: Context Collapse（⚠️ Stub 状态，暂未实现）==========
        // Claude Code 尚未实现此层级，参考：
        // - snipCompact.js 文件不存在
        // - contextCollapse/index.js 目录不存在
        // 当 L1-L3 不足以控制 token 数量时，直接跳过 L4，进入 L5 全量摘要
        //
        // if (contextCollapse.needsCollapse(currentTokens)) {
        //     CollapseResult l4Result = contextCollapse.recordCollapse(
        //             currentMessages.size(), currentTokens, sessionId);
        //     if (l4Result.wasCollapsed()) {
        //         executedLevels.add("L4");
        //         currentMessages = contextCollapse.projectView(currentMessages, sessionId);
        //         currentTokens = tokenEstimator.estimate(currentMessages);
        //         log.info("[Pipeline] L4 完成，折叠 {} 条消息，tokens: {}",
        //                 l4Result.messageCount(), currentTokens);
        //         if (!needsCompression(currentTokens)) {
        //             return new PipelineResult(currentMessages, true, originalTokens, currentTokens, executedLevels);
        //         }
        //     }
        // }
        // ========== L4 结束 ==========

        // 6. L5: Autocompact（最终兜底）
        // 直接执行，无需检查 L4 状态（L4 是 stub）
        if (autocompact.needsCompact(currentTokens)) {
            // 检查熔断器
            if (!autocompact.isCircuitBreakerTripped(sessionId)) {
                AutocompactResult l5Result = autocompact.compact(currentMessages, sessionId);
                if (l5Result.success()) {
                    currentMessages = l5Result.messages();
                    executedLevels.add("L5");
                    currentTokens = l5Result.compactedTokens();
                    log.info("[Pipeline] L5 完成，tokens: {}", currentTokens);
                } else {
                    log.warn("[Pipeline] L5 失败: {}", l5Result.errorMessage());
                }
            } else {
                log.warn("[Pipeline] L5 熔断器已触发，跳过压缩");
            }
        }

        // 7. 返回结果
        int finalTokens = currentTokens;
        boolean wasCompressed = !executedLevels.isEmpty();

        if (wasCompressed) {
            log.info("[Pipeline] 压缩完成，执行层级: {}，tokens: {} → {}",
                    executedLevels, originalTokens, finalTokens);
        }

        return new PipelineResult(currentMessages, wasCompressed, originalTokens, finalTokens, executedLevels);
    }

    @Override
    public double calculateUtilization(int tokens) {
        int contextWindow = properties.getModelContextWindow();
        if (contextWindow <= 0) {
            return 0.0;
        }
        return (double) tokens / contextWindow;
    }

    @Override
    public boolean needsCompression(int tokens) {
        return calculateUtilization(tokens) > properties.getContextThresholdRatio();
    }

    @Override
    public int getContextWindow() {
        return properties.getModelContextWindow();
    }

    /**
     * 应用 L1 Tool Result Budget
     *
     * <p>检查所有 ToolExecutionResultMessage，对超限结果应用预算裁剪。
     *
     * @param messages 消息列表
     * @param sessionId 会话ID
     * @return 处理结果，如果无需处理则返回 null
     */
    private PipelineResult applyL1(List<ChatMessage> messages, String sessionId) {
        List<ChatMessage> newMessages = new ArrayList<>();
        boolean anyTruncated = false;

        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolMsg) {
                String originalContent = toolMsg.text();

                // 检查是否需要裁剪
                if (toolResultBudget.needsTruncation(originalContent.length())) {
                    BudgetResult budget = toolResultBudget.process(
                            toolMsg.toolName(),
                            toolMsg.id(),
                            originalContent,
                            sessionId
                    );

                    if (budget.truncated()) {
                        // 创建新的清理后的消息
                        ToolExecutionResultMessage newMsg = ToolExecutionResultMessage.from(
                                toolMsg.id(),
                                toolMsg.toolName(),
                                budget.content()
                        );
                        newMessages.add(newMsg);
                        anyTruncated = true;
                        log.debug("[Pipeline] L1 截断工具 {} 结果，原始 {} 字符",
                                toolMsg.toolName(), originalContent.length());
                        continue;
                    }
                }
            }
            newMessages.add(message);
        }

        if (!anyTruncated) {
            return null;
        }

        int tokens = tokenEstimator.estimate(newMessages);
        return new PipelineResult(newMessages, true, 0, tokens, List.of("L1"));
    }
}
