package com.msz.resume.ai.chat.runtime.state;

import com.msz.resume.ai.agent.SubAgentType;
import com.msz.resume.ai.chat.compression.model.CacheUsage;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ！内层小循环状态实现！
 *
 * 就是存这一轮思考-行动循环里的所有变量
 *
 * 修改说明：新增 NUDGE_COUNT、LOW_YIELD_COUNT、LAST_OUTPUT_TOKEN_COUNT 三个字段，
 * 支持 Nudge 催促机制和递减收益检测
 */

public class QueryLoopState extends AgentState{

    /**
     * 会话ID
     * 从外层 SessionState 传入，用于工具结果持久化等场景
     */
    public static final String SESSION_ID = "sessionId";

    /**
     * 消息上下文列表
     * 图片描述：保存所有对话历史，包含用户输入、大模型回复、工具执行结果
     */
    public static final String MESSAGE_HISTORY = "messageHistory"; // 对话历史


    /**
     * 工具调用上下文
     * 图片描述：保存当前工具调用的相关信息（工具参数、执行环境等）
     */
    public static final String  TOOL_USE_CONTEXT= "toolUseContext"; // 工具调用上下文


    /**
     * 上下文压缩追踪信息
     * 图片描述：记录上下文压缩的进度，标记哪些消息被压缩
     */
    public static final String AUTO_COMPACT_TRACKING = "autoCompactTracking";


    /**
     * Token 超限恢复次数
     * 大模型返回 Token 超限后重试的次数
     */
    public static final String MAX_TOKEN_RECOVERY_COUNT = "maxOutputTokensRecoveryCount"; // Token超限重试次数


    /**
     * 是否尝试过 Reactive 压缩
     * 避免重复尝试上下文压缩
     */
    public static final String HAS_ATTEMPTED_COMPACT = "hasAttemptedReactiveCompact"; // 是否试过压缩


    /**
     * 单轮内迭代次数
     * 思考 → 行动 循环执行了多少次
     */
    public static final String TURN_COUNT = "turnCount"; // 循环迭代次数

    /**
     * 上一次迭代的跳转原因
     * 例如：工具执行、错误恢复、流程结束
     */
    public static final String TRANSITION = "transition"; // 上一次跳转原因

    /**
     * LLM 错误类型
     * 当 TRANSITION=error 时，记录更具体的错误分类（rate_limit/upstream_5xx/timeout/bad_request_or_schema/unknown）
     */
    public static final String ERROR_TYPE = "errorType";

    /**
     * LLM 错误消息
     * 记录最近一次 LLM 调用失败的简要错误信息，便于日志和错误恢复节点诊断
     */
    public static final String ERROR_MESSAGE = "errorMessage";

    /**
     * Nudge 催促次数
     * LLM 返回纯文本后追加催促消息的次数，用于防止无限循环
     */
    public static final String NUDGE_COUNT = "nudgeCount"; // Nudge催促次数

    /**
     * 低产出连续次数
     * 连续 Nudge 后 LLM 增量输出 < 500 token 的次数，达到3次则停止循环
     */
    public static final String LOW_YIELD_COUNT = "lowYieldCount"; // 低产出连续次数

    /**
     * 上一次 LLM 输出的 token 数
     * 用于递减收益检测：如果连续多次输出很少 token，说明 LLM 已无实质进展
     */
    public static final String LAST_OUTPUT_TOKEN_COUNT = "lastOutputTokenCount"; // 上次LLM输出token数

    /**
     * 用户上下文
     * 从外层 SessionState 传入，用于构建系统提示词的 user_context/user_preferences section
     */
    public static final String USER_CONTEXT = "userContext"; // 用户上下文

    /**
     * OpenViking 租户身份
     * 显式跟随内层图、工具执行和子 Agent 传递，避免依赖 ThreadLocal 跨异步线程。
     */
    public static final String OPENVIKING_IDENTITY = "openVikingIdentity";

    /**
     * 本会话已经自动召回并注入过的 OpenViking URI。
     * value 表示已注入层级：abstract / overview，用于避免连续轮次重复注入同一段上下文，
     * 同时允许从摘要升级为 overview。
     */
    public static final String SURFACED_OPENVIKING_URIS = "surfacedOpenVikingUris";

    /**
     * 已发现的延迟工具名称集合
     * LLM 通过 toolSearch 发现的延迟工具名称，下一轮 LLM 请求会包含这些工具的完整 schema
     */
    public static final String DISCOVERED_TOOLS = "discoveredTools"; // 已发现的延迟工具名称

    /**
     * 缓存使用情况
     * 记录最近一次 LLM API 调用的缓存命中情况，包含命中率、热度状态等
     */
    public static final String CACHE_USAGE = "cacheUsage"; // 缓存使用情况

    // ==================== 任务规划支持 ====================

    /**
     * 任务计划列表
     * LLM 通过 TaskPlanTool 创建和管理的任务列表，参与状态机流转
     */
    public static final String TASK_PLAN = "taskPlan";

    // ==================== AskUserQuestion 阻塞式支持 ====================

    /**
     * 挂起会话ID
     * 当 AskUserQuestion 工具被调用时，生成唯一的 pendingId 用于恢复
     */
    public static final String PENDING_ID = "pendingId";

    /**
     * 待回答的问题列表
     * AskUserQuestion 工具执行后设置，传递给前端渲染问答 UI
     */
    public static final String PENDING_QUESTIONS = "pendingQuestions";

    /**
     * 工具调用ID
     * 用于构造 ToolExecutionResultMessage
     */
    public static final String PENDING_TOOL_CALL_ID = "pendingToolCallId";

    // ==================== 子Agent（SubGraphNode）支持 ====================

    /**
     * 是否为子Agent模式
     * true 表示当前状态运行在子状态机中，需使用精简提示词和受限工具集
     */
    public static final String IS_SUB_AGENT = "isSubAgent";

    /**
     * 子Agent允许使用的工具名称集合
     * 不包含 spawnAgent、askUserQuestion、askMultipleQuestions
     */
    public static final String AVAILABLE_TOOLS = "availableTools";

    /**
     * 子Agent最大迭代轮次
     * 0 表示父图模式（无限制）；>0 表示子Agent模式的最大轮次
     */
    public static final String MAX_TURNS = "maxTurns";

    /**
     * 子Agent的任务描述
     * 由父图通过 spawnAgent 传入，用于构建子Agent的系统提示词
     */
    public static final String SUB_AGENT_TASK = "subAgentTask";

    /**
     * 子Agent类型
     * 决定子Agent的工具白名单/黑名单配置
     * @see com.msz.resume.ai.agent.SubAgentType
     */
    public static final String SUB_AGENT_TYPE = "subAgentType";

    /**
     * 子Agent累计的 LLM 输入 Token 数
     * 每次 CallLlmNode 调用后累加，最终汇总到 SUB_AGENT_TOKEN_ACCUMULATOR
     */
    public static final String SUB_AGENT_INPUT_TOKENS = "subAgentInputTokens";

    /**
     * 子Agent累计的 LLM 输出 Token 数
     * 每次 CallLlmNode 调用后累加，最终汇总到 SUB_AGENT_TOKEN_ACCUMULATOR
     */
    public static final String SUB_AGENT_OUTPUT_TOKENS = "subAgentOutputTokens";

    /**
     * 子Agent Token 用量累加器
     * 每个子Agent执行完后，将 inputTokens/outputTokens 以 Map 形式追加到此列表
     * UsageStatNode 从此列表聚合到 SessionState 的 totalInputTokens/totalOutputTokens
     */
    public static final String SUB_AGENT_TOKEN_ACCUMULATOR = "subAgentTokenAccumulator";

    // ==================== 实时 Trace 支持 ====================

    /**
     * 当前流式运行 ID。
     * 同一个 session 的多次 SSE 执行必须使用不同 runId，避免事件串流。
     */
    public static final String TRACE_RUN_ID = "traceRunId";

    /**
     * 当前 Agent 标识。
     * 主 Agent 固定为 main，子 Agent 使用 sub_xxx 形式。
     */
    public static final String TRACE_AGENT_ID = "traceAgentId";

    /**
     * 当前 Agent 展示名称。
     * 例如 Main Agent / Explore #1 / Plan #2。
     */
    public static final String TRACE_AGENT_LABEL = "traceAgentLabel";

    /**
     * 当前 Agent 作用域。
     * 取值：main / sub。
     */
    public static final String TRACE_AGENT_SCOPE = "traceAgentScope";


    /**
     * 状态字段说明
     * 1. MESSAGES: 保存所有对话历史，包含用户输入、大模型回复、工具执行结果
     * 2. TOOL_USE_CONTEXT: 保存当前工具调用的相关信息（工具参数、执行环境等）
     * 3. AUTO_COMPACT_TRACKING: 记录上下文压缩的进度，标记哪些消息被压缩
     * 4. MAX_TOKEN_RECOVERY_COUNT: 大模型返回 Token 超限后重试的次数
     * 5. HAS_ATTEMPTED_COMPACT: 是否试过压缩
     * 6. TURN_COUNT: 思考 → 行动 循环执行了多少次
     * 7. TRANSITION: 上一次跳转原因
     * 8. NUDGE_COUNT: Nudge 催促次数，防止无限循环
     * 9. LOW_YIELD_COUNT: 低产出连续次数，递减收益检测
     * 10. LAST_OUTPUT_TOKEN_COUNT: 上一次 LLM 输出的 token 数
     */




    //appender 追加策略  value 覆盖策略
    // ArrayList::new    用 ArrayList 存消息，每次都创建一个新的空 ArrayList
    // 使用 Map.ofEntries 因为字段超过5个，Map.of() 最多只支持10个参数（5对）
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(SESSION_ID, Channels.base(() -> "")),
            Map.entry(MESSAGE_HISTORY, Channels.appender(ArrayList::new)),
            Map.entry(TOOL_USE_CONTEXT, Channels.appender(ArrayList::new)),
            Map.entry(AUTO_COMPACT_TRACKING, Channels.appender(ArrayList::new)),
            Map.entry(MAX_TOKEN_RECOVERY_COUNT, Channels.base(() -> 0)),
            Map.entry(HAS_ATTEMPTED_COMPACT, Channels.base(() -> false)),
            Map.entry(TURN_COUNT, Channels.base(() -> 0)),
            Map.entry(TRANSITION, Channels.base(() -> "")),
            Map.entry(ERROR_TYPE, Channels.base(() -> "")),
            Map.entry(ERROR_MESSAGE, Channels.base(() -> "")),
            Map.entry(NUDGE_COUNT, Channels.base(() -> 0)),
            Map.entry(LOW_YIELD_COUNT, Channels.base(() -> 0)),
            Map.entry(LAST_OUTPUT_TOKEN_COUNT, Channels.base(() -> 0)),
            Map.entry(USER_CONTEXT, Channels.base(UserProfile::empty)),
            Map.entry(OPENVIKING_IDENTITY, Channels.base(OpenVikingIdentity::empty)),
            Map.entry(SURFACED_OPENVIKING_URIS, Channels.base(() -> new LinkedHashMap<String, String>())),
            Map.entry(DISCOVERED_TOOLS, Channels.base(() -> new LinkedHashSet<String>())),
            Map.entry(CACHE_USAGE, Channels.base(CacheUsage::empty)),
            // 任务规划支持（初始值为空列表）
            Map.entry(TASK_PLAN, Channels.base(ArrayList::new)),
            // AskUserQuestion 阻塞式支持（初始值为空字符串/空列表，表示无挂起）
            Map.entry(PENDING_ID, Channels.base(() -> "")),
            Map.entry(PENDING_QUESTIONS, Channels.base(ArrayList::new)),
            Map.entry(PENDING_TOOL_CALL_ID, Channels.base(() -> "")),
            // 子Agent支持（初始值：非子Agent模式，空工具集，无轮次限制，空任务，0 token，空累加器）
            Map.entry(IS_SUB_AGENT, Channels.base(() -> false)),
            Map.entry(AVAILABLE_TOOLS, Channels.base(() -> new LinkedHashSet<String>())),
            Map.entry(MAX_TURNS, Channels.base(() -> 0)),
            Map.entry(SUB_AGENT_TASK, Channels.base(() -> "")),
            Map.entry(SUB_AGENT_TYPE, Channels.base(() -> SubAgentType.General)),
            Map.entry(SUB_AGENT_INPUT_TOKENS, Channels.base(() -> 0)),
            Map.entry(SUB_AGENT_OUTPUT_TOKENS, Channels.base(() -> 0)),
            Map.entry(SUB_AGENT_TOKEN_ACCUMULATOR, Channels.appender(ArrayList::new)),
            Map.entry(TRACE_RUN_ID, Channels.base(() -> "")),
            Map.entry(TRACE_AGENT_ID, Channels.base(() -> "main")),
            Map.entry(TRACE_AGENT_LABEL, Channels.base(() -> "Main Agent")),
            Map.entry(TRACE_AGENT_SCOPE, Channels.base(() -> "main"))
    );





    public QueryLoopState(Map<String, Object> initData) {
        super(initData);
    }

    /** ====================== 便捷取值方法（全部状态字段）======================== */

    //调用 messages() 就能拿到聊天消息；如果没有任何消息，就自动返回一个空列表，绝对不会让程序崩溃报错。
// 状态字段的便捷访问方法

    // 从状态里拿messages变量
    public List<ChatMessage> getMessages() {
        // 从状态里拿messages变量，如果没有，就返回个空列表
        return this.<List<ChatMessage>>value(MESSAGE_HISTORY).orElse(new ArrayList<>());
    }

    // 从状态里拿toolUseContext变量
    public List<Object> getToolUseContext() {
        return this.<List<Object>>value(TOOL_USE_CONTEXT).orElse(new ArrayList<>());
    }

    // 从状态里拿autoCompactTracking变量
    public List<Object> getAutoCompactTracking() {
        return this.<List<Object>>value(AUTO_COMPACT_TRACKING).orElse(new ArrayList<>());
    }

    // 从状态里拿maxTokenRecoveryCount变量
    public int getMaxTokenRecoveryCount() {
        return this.<Integer>value(MAX_TOKEN_RECOVERY_COUNT).orElse(0);
    }

    //拿是否尝试过压缩
    public boolean hasCompacted() {
        return this.<Boolean>value(HAS_ATTEMPTED_COMPACT).orElse(false);
    }

    //拿循环次数
    public int getTurnCount() {
        return this.<Integer>value(TURN_COUNT).orElse(0);
    }


    //拿上次循环跳转原因
    public String getTransition() {
        return this.<String>value(TRANSITION).orElse(null);
    }

    // 拿 LLM 错误类型
    public String getErrorType() {
        String type = this.<String>value(ERROR_TYPE).orElse("");
        return (type != null && !type.isBlank()) ? type : null;
    }

    // 拿 LLM 错误消息
    public String getErrorMessage() {
        String message = this.<String>value(ERROR_MESSAGE).orElse("");
        return (message != null && !message.isBlank()) ? message : null;
    }

    //拿Nudge催促次数
    public int getNudgeCount() {
        return this.<Integer>value(NUDGE_COUNT).orElse(0);
    }

    //拿低产出连续次数
    public int getLowYieldCount() {
        return this.<Integer>value(LOW_YIELD_COUNT).orElse(0);
    }

    //拿上次LLM输出的token数
    public int getLastOutputTokenCount() {
        return this.<Integer>value(LAST_OUTPUT_TOKEN_COUNT).orElse(0);
    }

    //拿用户上下文
    public UserProfile getUserContext() {
        return this.<UserProfile>value(USER_CONTEXT).orElse(UserProfile.empty());
    }

    public OpenVikingIdentity getOpenVikingIdentity() {
        OpenVikingIdentity identity = this.<OpenVikingIdentity>value(OPENVIKING_IDENTITY).orElse(OpenVikingIdentity.empty());
        return identity.isComplete() ? identity : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getSurfacedOpenVikingUris() {
        Map<?, ?> surfaced = this.<Map<?, ?>>value(SURFACED_OPENVIKING_URIS).orElse(Map.of());
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : surfaced.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    //拿已发现的延迟工具名称集合
    @SuppressWarnings("unchecked")
    public Set<String> getDiscoveredTools() {
        return this.<Set<String>>value(DISCOVERED_TOOLS).orElse(new LinkedHashSet<>());
    }

    //拿缓存使用情况
    public CacheUsage getCacheUsage() {
        return this.<CacheUsage>value(CACHE_USAGE).orElse(CacheUsage.empty());
    }

    //拿会话ID
    public String getSessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }

    // ==================== 任务规划便捷方法 ====================

    /**
     * 获取任务计划列表
     *
     * @return 任务列表，无任务计划时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTaskPlan() {
        return this.<List<Map<String, Object>>>value(TASK_PLAN).orElse(new ArrayList<>());
    }

    // ==================== AskUserQuestion 便捷方法 ====================

    /**
     * 获取挂起会话ID
     * 返回 null 表示没有挂起的 AskUserQuestion
     */
    public String getPendingId() {
        String id = this.<String>value(PENDING_ID).orElse("");
        return (id != null && !id.isBlank()) ? id : null;
    }

    /**
     * 获取待回答的问题列表
     * 返回 null 表示没有待回答的问题
     */
    @SuppressWarnings("unchecked")
    public List<?> getPendingQuestions() {
        List<?> questions = this.<List<?>>value(PENDING_QUESTIONS).orElse(new ArrayList<>());
        return (questions != null && !questions.isEmpty()) ? questions : null;
    }

    /**
     * 获取工具调用ID
     * 返回 null 表示没有挂起的工具调用
     */
    public String getPendingToolCallId() {
        String id = this.<String>value(PENDING_TOOL_CALL_ID).orElse("");
        return (id != null && !id.isBlank()) ? id : null;
    }

    // ==================== 子Agent 便捷方法 ====================

    /**
     * 是否为子Agent模式
     */
    public boolean isSubAgent() {
        return this.<Boolean>value(IS_SUB_AGENT).orElse(false);
    }

    /**
     * 获取子Agent允许使用的工具名称集合
     */
    @SuppressWarnings("unchecked")
    public Set<String> getAvailableTools() {
        return this.<Set<String>>value(AVAILABLE_TOOLS).orElse(new LinkedHashSet<>());
    }

    /**
     * 获取子Agent最大迭代轮次
     * 0 表示父图模式（无限制）
     */
    public int getMaxTurns() {
        return this.<Integer>value(MAX_TURNS).orElse(0);
    }

    /**
     * 获取子Agent的任务描述
     */
    public String getSubAgentTask() {
        return this.<String>value(SUB_AGENT_TASK).orElse("");
    }

    /**
     * 获取子Agent类型
     * 默认为 GENERAL
     */
    public SubAgentType getSubAgentType() {
        return this.<SubAgentType>value(SUB_AGENT_TYPE).orElse(SubAgentType.General);
    }

    /**
     * 获取子Agent累计输入Token数
     */
    public int getSubAgentInputTokens() {
        return this.<Integer>value(SUB_AGENT_INPUT_TOKENS).orElse(0);
    }

    /**
     * 获取子Agent累计输出Token数
     */
    public int getSubAgentOutputTokens() {
        return this.<Integer>value(SUB_AGENT_OUTPUT_TOKENS).orElse(0);
    }

    /**
     * 获取子Agent Token用量累加器
     * 每个子Agent执行完后追加一个 Map（包含 inputTokens 和 outputTokens）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Integer>> getSubAgentTokenAccumulator() {
        return this.<List<Map<String, Integer>>>value(SUB_AGENT_TOKEN_ACCUMULATOR).orElse(new ArrayList<>());
    }

    public String getTraceRunId() {
        String runId = this.<String>value(TRACE_RUN_ID).orElse("");
        return (runId != null && !runId.isBlank()) ? runId : null;
    }

    public String getTraceAgentId() {
        String agentId = this.<String>value(TRACE_AGENT_ID).orElse("main");
        return (agentId != null && !agentId.isBlank()) ? agentId : "main";
    }

    public String getTraceAgentLabel() {
        String agentLabel = this.<String>value(TRACE_AGENT_LABEL).orElse("Main Agent");
        return (agentLabel != null && !agentLabel.isBlank()) ? agentLabel : "Main Agent";
    }

    public String getTraceAgentScope() {
        String agentScope = this.<String>value(TRACE_AGENT_SCOPE).orElse("main");
        return (agentScope != null && !agentScope.isBlank()) ? agentScope : "main";
    }

}
