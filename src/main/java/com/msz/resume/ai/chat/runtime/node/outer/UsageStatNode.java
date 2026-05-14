package com.msz.resume.ai.chat.runtime.node.outer;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * 用量统计节点：统计Token用量
 *
 * 核心功能：
 * 1. 区分输入Token（用户+系统+工具）和输出Token（AI回复）
 * 2. 采用增量统计方式，避免重复计算历史消息
 * 3. 累计整个会话的Token使用量
 * 4. 聚合子Agent的Token用量到会话总量
 *
 * 工作原理：
 * - 通过 lastStatIndex 记录上次统计到的消息位置
 * - 每次只统计新增的消息（subList截取）
 * - 将新增Token数累加到历史总量
 * - 从 SUB_AGENT_TOKEN_ACCUMULATOR 读取子Agent的Token用量并累加
 * - 更新 lastStatIndex 为当前消息总数
 */
@Slf4j
@Component
public class UsageStatNode implements AsyncNodeAction<SessionState> {

    /**
     * 状态字段：记录上次统计时的消息索引位置
     * 用于实现增量统计，避免重复计算
     */
    private static final String LAST_STAT_INDEX = "lastStatIndex";

    /**
     * Token估算器：LangChain4j提供的Token数量估算工具
     * 通过构造函数注入，由Spring容器管理
     */
    private final TokenCountEstimator tokenCountEstimator;

    /**
     * 构造函数注入依赖
     * @param tokenCountEstimator Token数量估算器
     */
    public UsageStatNode(TokenCountEstimator tokenCountEstimator) {
        this.tokenCountEstimator = tokenCountEstimator;
    }

    /**
     * 执行Token用量统计
     *
     * 处理流程：
     * 1. 获取当前会话的所有消息
     * 2. 读取上次统计的位置索引
     * 3. 如果没有新消息，直接返回空Map
     * 4. 提取新增的消息列表
     * 5. 分别计算输入Token和输出Token
     * 6. 聚合子Agent的Token用量
     * 7. 累加到历史总量
     * 8. 更新统计位置索引
     *
     * @param currentState 当前会话状态
     * @return 包含更新后的累计Token数和新的统计索引
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(SessionState currentState) {
        // 异步执行统计任务，避免阻塞主线程
        OpenVikingIdentity identity = OpenVikingIdentitySupport.fromSessionState(currentState);
        return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
            log.info("[用量统计] 会话ID: {}", currentState.getSessionId());

            // 步骤1：获取内层状态中的完整消息历史
            var innerState = currentState.getInnerState();
            List<ChatMessage> allMessages = innerState.getMessages();

            // 步骤2：读取上次统计结束时的消息索引（默认为0，表示从未统计过）
            int lastStatIndex = currentState.<Integer>value(LAST_STAT_INDEX).orElse(0);

            // 步骤3：检查是否有新消息，如果没有则跳过消息统计
            int messageInputTokens = 0;
            int messageOutputTokens = 0;

            if (lastStatIndex < allMessages.size()) {
                // 步骤4：截取新增的消息列表（从上次统计位置到当前末尾）
                List<ChatMessage> newMessages = allMessages.subList(lastStatIndex, allMessages.size());

                // 步骤5：分别计算输入Token和输出Token
                messageInputTokens = calculateInputTokens(newMessages);
                messageOutputTokens = calculateOutputTokens(newMessages);
            }

            // 步骤6：聚合子Agent的Token用量
            int subAgentInputTokens = aggregateSubAgentTokens(innerState, "inputTokens");
            int subAgentOutputTokens = aggregateSubAgentTokens(innerState, "outputTokens");

            // 步骤7：读取历史累计Token数
            int previousTotalInput = currentState.<Integer>value(SessionState.TOTAL_INPUT_TOKENS).orElse(0);
            int previousTotalOutput = currentState.<Integer>value(SessionState.TOTAL_OUTPUT_TOKENS).orElse(0);

            // 步骤8：计算新的累计总量（包含子Agent用量）
            int newTotalInput = previousTotalInput + messageInputTokens + subAgentInputTokens;
            int newTotalOutput = previousTotalOutput + messageOutputTokens + subAgentOutputTokens;

            // 步骤9：打印详细的统计日志
            if (subAgentInputTokens > 0 || subAgentOutputTokens > 0) {
                log.info("[用量统计] 本次: input={}, output={}; 子Agent: input={}, output={}; 累计: input={}, output={}",
                        messageInputTokens, messageOutputTokens,
                        subAgentInputTokens, subAgentOutputTokens,
                        newTotalInput, newTotalOutput);
            } else {
                log.info("[用量统计] 本次: input={}, output={}, total={}; 累计: input={}, output={}, total={}",
                        messageInputTokens, messageOutputTokens,
                        messageInputTokens + messageOutputTokens,
                        newTotalInput, newTotalOutput, newTotalInput + newTotalOutput);
            }

            // 步骤10：返回更新后的状态
            Map<String, Object> update = new java.util.HashMap<>();
            update.put(SessionState.TOTAL_INPUT_TOKENS, newTotalInput);
            update.put(SessionState.TOTAL_OUTPUT_TOKENS, newTotalOutput);
            update.put(LAST_STAT_INDEX, allMessages.size());
            update.put(SessionState.OPENVIKING_IDENTITY, identity);
            return update;
        });
    }

    /**
     * 聚合子Agent的Token用量
     *
     * 从 QueryLoopState.SUB_AGENT_TOKEN_ACCUMULATOR 中读取所有子Agent的Token记录，
     * 累加指定字段的值。
     *
     * @param innerState 内层循环状态
     * @param fieldName  要累加的字段名（"inputTokens" 或 "outputTokens"）
     * @return 子Agent Token用量的累加值
     */
    @SuppressWarnings("unchecked")
    private int aggregateSubAgentTokens(QueryLoopState innerState, String fieldName) {
        List<Map<String, Integer>> accumulator = innerState.getSubAgentTokenAccumulator();
        if (accumulator == null || accumulator.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Map<String, Integer> entry : accumulator) {
            Integer value = entry.get(fieldName);
            if (value != null) {
                total += value;
            }
        }

        if (total > 0) {
            log.debug("[用量统计] 子Agent {} 累加: {}", fieldName, total);
        }
        return total;
    }

    /**
     * 计算输入Token数量
     *
     * 输入Token包括：
     * - UserMessage：用户的提问和指令
     * - SystemMessage：系统提示词和角色设定
     * - ToolExecutionResultMessage：工具执行结果
     *
     * 这些消息都是发送给AI模型的"输入"，需要消耗输入Token配额
     *
     * @param messages 待统计的消息列表
     * @return 输入Token数量
     */
    private int calculateInputTokens(List<ChatMessage> messages) {
        // 过滤出所有输入类型的消息
        List<ChatMessage> inputMessages = messages.stream()
                .filter(msg -> msg instanceof UserMessage           // 用户消息
                        || msg instanceof SystemMessage         // 系统消息
                        || msg instanceof ToolExecutionResultMessage)  // 工具结果
                .toList();

        // 如果没有输入消息，返回0；否则使用估算器计算Token数
        return inputMessages.isEmpty() ? 0 : tokenCountEstimator.estimateTokenCountInMessages(inputMessages);
    }

    /**
     * 计算输出Token数量
     *
     * 输出Token包括：
     * - AiMessage：AI助手生成的回复内容
     *
     * 这些消息是AI模型生成的"输出"，需要消耗输出Token配额
     * （通常输出Token的单价比输入Token更高）
     *
     * @param messages 待统计的消息列表
     * @return 输出Token数量
     */
    private int calculateOutputTokens(List<ChatMessage> messages) {
        // 过滤出所有AI回复消息
        List<ChatMessage> outputMessages = messages.stream()
                .filter(msg -> msg instanceof AiMessage)  // AI生成的消息
                .toList();

        // 如果没有输出消息，返回0；否则使用估算器计算Token数
        return outputMessages.isEmpty() ? 0 : tokenCountEstimator.estimateTokenCountInMessages(outputMessages);
    }
}
