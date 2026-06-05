package com.msz.resume.ai.chat.compression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 压缩后恢复器
 *
 * <p>在 L5 Autocompact 执行后，恢复最近调用的 Skill、当前 Plan 等信息。
 *
 * <p>恢复优先级：
 * <ol>
 *   <li>最近调用的 Skill（按时间倒序）</li>
 *   <li>当前活跃的 Plan</li>
 * </ol>
 *
 * <p>当前实现：预留接口，返回空列表。待 Skill 和 Plan 功能完成后接入。
 *
 * <p>预算控制：
 * <ul>
 *   <li>Skill 恢复预算：25,000 tokens</li>
 *   <li>单个 Skill 上限：5,000 tokens</li>
 *   <li>Plan 恢复预算：10,000 tokens</li>
 * </ul>
 */
@Slf4j
@Component
public class PostCompactRestorer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * ThreadLocal 存储当前请求的任务计划
     * CallLlmNode 在调用 pipeline 前设置，restore() 中读取用于恢复
     */
    private static final ThreadLocal<List<Map<String, Object>>> CURRENT_TASK_PLAN =
            new ThreadLocal<>();

    private final TokenEstimator tokenEstimator;
    private final AutocompactConfig config;

    public PostCompactRestorer(TokenEstimator tokenEstimator, AutocompactConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config;
    }

    /**
     * 设置当前任务计划（供 CallLlmNode 调用）
     *
     * @param taskPlan 当前任务计划
     */
    public static void setTaskPlan(List<Map<String, Object>> taskPlan) {
        CURRENT_TASK_PLAN.set(taskPlan);
    }

    /**
     * 清理 ThreadLocal（请求结束后必须调用）
     */
    public static void clearTaskPlan() {
        CURRENT_TASK_PLAN.remove();
    }

    /**
     * 恢复最近调用的 Skill 和当前 Plan
     *
     * <p>当前为预留实现，返回空列表。
     * 待 Skill 和 Plan 功能完成后，从数据库读取并注入。
     *
     * @param sessionId        会话 ID
     * @param preservedMessages 保留的消息列表（用于去重）
     * @return 恢复的消息列表（作为 attachment 追加到摘要后面）
     */
    public List<ChatMessage> restore(String sessionId, List<ChatMessage> preservedMessages) {
        List<ChatMessage> attachments = new ArrayList<>();

        // 1. 恢复最近调用的 Skill
        List<ChatMessage> skillAttachments = restoreRecentSkills(sessionId, preservedMessages);
        attachments.addAll(skillAttachments);

        // 2. 恢复当前 Plan
        ChatMessage planAttachment = restoreCurrentPlan(sessionId);
        if (planAttachment != null) {
            attachments.add(planAttachment);
        }

        if (!attachments.isEmpty()) {
            log.info("[PostCompactRestorer] 恢复 {} 条附加消息", attachments.size());
        }

        return attachments;
    }

    /**
     * 恢复最近调用的 Skill
     *
     * <p>当前为预留实现。完整实现后：
     * <ol>
     *   <li>从 skill_invocation 表查询最近调用的 Skill</li>
     *   <li>按时间倒序排列，最近优先</li>
     *   <li>过滤已在保留消息中的 Skill</li>
     *   <li>按预算截断</li>
     * </ol>
     *
     * @param sessionId        会话 ID
     * @param preservedMessages 保留的消息列表
     * @return Skill 消息列表
     */
    private List<ChatMessage> restoreRecentSkills(String sessionId, List<ChatMessage> preservedMessages) {
        // TODO: 待 Skill 功能完成后实现
        // 1. 从数据库获取最近调用的 Skill
        // List<SkillInvocation> invocations = skillInvocationRepo
        //     .findRecentBySession(sessionId, config.getMaxSkillsToRestore());

        // 2. 提取已保留消息中已包含的 Skill 名称
        Set<String> alreadyIncludedSkills = extractSkillNames(preservedMessages);

        // 3. 遍历并构建消息，按预算截断
        // int totalTokens = 0;
        // for (SkillInvocation invocation : invocations) {
        //     if (alreadyIncludedSkills.contains(invocation.getSkillName())) continue;
        //     // ... 构建 UserMessage
        // }

        log.debug("[PostCompactRestorer] Skill 恢复功能待实现");
        return List.of();
    }

    /**
     * 恢复当前任务计划
     *
     * <p>从 ThreadLocal 读取 taskPlan（由 CallLlmNode 设置），
     * 将未完成的任务序列化为 UserMessage 注入到压缩后的消息中。
     *
     * @param sessionId 会话 ID
     * @return Plan 消息，无则返回 null
     */
    private ChatMessage restoreCurrentPlan(String sessionId) {
        List<Map<String, Object>> taskPlan = CURRENT_TASK_PLAN.get();
        if (taskPlan == null || taskPlan.isEmpty()) {
            log.debug("[PostCompactRestorer] 无任务计划需要恢复");
            return null;
        }

        try {
            String planJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(taskPlan);
            String content = "[压缩后恢复 - 当前任务计划]\n\n" + truncateContent(planJson, config.getPlanRestoreBudget());
            log.info("[PostCompactRestorer] 恢复任务计划: {} 个任务", taskPlan.size());
            return UserMessage.from(content);
        } catch (JsonProcessingException e) {
            log.warn("[PostCompactRestorer] 任务计划序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从保留消息中提取已包含的 Skill 名称
     *
     * <p>用于去重，避免恢复已在保留消息中的 Skill。
     *
     * @param messages 消息列表
     * @return Skill 名称集合
     */
    private Set<String> extractSkillNames(List<ChatMessage> messages) {
        Set<String> names = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage userMsg) {
                String text = ChatMessageTextExtractor.userText(userMsg);
                if (text != null && text.contains("[Skill: ")) {
                    // 提取 Skill 名称，格式：[Skill: skill-name]
                    int start = text.indexOf("[Skill: ") + 8;
                    int end = text.indexOf("]", start);
                    if (end > start) {
                        names.add(text.substring(start, end));
                    }
                }
            }
        }
        return names;
    }

    /**
     * 截断内容到指定 token 预算
     *
     * <p>使用简单的字符数估算：1 token ≈ 4 chars
     *
     * @param content 原始内容
     * @param budget  token 预算
     * @return 截断后的内容
     */
    private String truncateContent(String content, int budget) {
        if (content == null) {
            return "";
        }

        // 简单实现：按比例截断
        // 假设 1 token ≈ 4 chars
        int charBudget = budget * 4;
        if (content.length() <= charBudget) {
            return content;
        }

        return content.substring(0, charBudget) + "\n...[内容已截断]";
    }

    // ==================== 以下为完整实现时需要的接口 ====================

    /**
     * Skill 调用记录（内部类，完整实现时替换为数据库实体）
     */
    public record SkillInvocation(
        String skillName,
        String skillContent,
        long invokedAt
    ) {}

    /**
     * Plan 记录（内部类，完整实现时替换为数据库实体）
     */
    public record Plan(
        String content,
        String status
    ) {}
}
