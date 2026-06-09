package com.msz.resume.ai.chat.runtime.subagent;

/**
 * 子Agent执行结果DTO
 *
 * <p>子Agent执行完成后的结构化结果，包含执行状态、轮次、token用量等信息。
 * 由 SubGraphNode 返回，在 ExecuteToolNode 中格式化为 ToolExecutionResultMessage。
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li><b>success</b>: 子Agent正常完成任务，摘要中包含结果</li>
 *   <li><b>wrapped_up</b>: 子Agent达到探索轮次限制后完成强制收束，摘要中包含最终结果</li>
 *   <li><b>max_turns_exceeded</b>: 子Agent达到最大轮次限制，摘要中包含当前进展</li>
 *   <li><b>error</b>: 子Agent执行异常，摘要中包含错误信息</li>
 * </ul>
 */
public record SubAgentResult(
        /** 执行状态：success / wrapped_up / max_turns_exceeded / error */
        String status,
        /** 实际执行的轮次 */
        int turnCount,
        /** 最大轮次限制 */
        int maxTurns,
        /** 子Agent的最终AI消息摘要 */
        String summary,
        /** 子Agent累计的输入Token数 */
        int inputTokens,
        /** 子Agent累计的输出Token数 */
        int outputTokens
) {
    /**
     * 创建成功结果
     */
    public static SubAgentResult success(String summary, int turnCount, int maxTurns,
                                           int inputTokens, int outputTokens) {
        return new SubAgentResult("success", turnCount, maxTurns, summary, inputTokens, outputTokens);
    }

    /**
     * 创建强制收束结果
     */
    public static SubAgentResult wrappedUp(String summary, int turnCount, int maxTurns,
                                           int inputTokens, int outputTokens) {
        return new SubAgentResult("wrapped_up", turnCount, maxTurns, summary, inputTokens, outputTokens);
    }

    /**
     * 创建轮次超限结果
     */
    public static SubAgentResult maxTurnsExceeded(String summary, int turnCount, int maxTurns,
                                                    int inputTokens, int outputTokens) {
        return new SubAgentResult("max_turns_exceeded", turnCount, maxTurns, summary, inputTokens, outputTokens);
    }

    /**
     * 创建错误结果
     */
    public static SubAgentResult error(String errorMessage, int turnCount, int maxTurns,
                                        int inputTokens, int outputTokens) {
        return new SubAgentResult("error", turnCount, maxTurns, errorMessage, inputTokens, outputTokens);
    }

    /**
     * 是否执行成功
     */
    public boolean isSuccess() {
        return "success".equals(status);
    }

    /**
     * 是否完成强制收束
     */
    public boolean isWrappedUp() {
        return "wrapped_up".equals(status);
    }

    /**
     * 是否因轮次超限终止
     */
    public boolean isMaxTurnsExceeded() {
        return "max_turns_exceeded".equals(status);
    }

    /**
     * 是否执行出错
     */
    public boolean isError() {
        return "error".equals(status);
    }
}
