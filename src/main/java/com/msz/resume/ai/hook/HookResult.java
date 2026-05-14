package com.msz.resume.ai.hook;

/**
 * Hook 执行结果
 *
 * <p>工具调用拦截点（PreToolUse / PostToolUse）的返回值。
 * 三种结果：
 * <ul>
 *   <li>{@link #continueResult()} — 放行，继续执行下一个 Hook 或工具本身</li>
 *   <li>{@link #block(String)} — 阻断，终止 Hook 链并取消工具执行</li>
 *   <li>{@link #modifyArgs(java.util.Map)} — 修改参数后放行，替换工具的入参</li>
 * </ul>
 *
 * <h2>设计参考</h2>
 * Claude Code 的 Hook 系统使用 exit code 0/2 + stdout JSON 控制流程。
 * JARVIS 的 HookResult 是其 Java 等价物：用类型安全的工厂方法替代 exit code。
 *
 * @see HookService
 */
public sealed interface HookResult {

    /**
     * 是否为阻断结果
     */
    boolean isBlocked();

    /**
     * 阻断原因（仅 block 结果有值）
     */
    String blockReason();

    /**
     * 修改后的参数（仅 modifyArgs 结果有值）
     */
    java.util.Map<String, Object> modifiedArgs();

    /**
     * 挂起会话ID（仅 pending 结果有值）
     */
    String pendingId();

    /**
     * 待回答问题（仅 pending 结果有值）
     */
    java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> pendingQuestions();

    /**
     * 确认后需要执行的工具请求（仅 pending 结果有值）
     */
    dev.langchain4j.agent.tool.ToolExecutionRequest pendingConfirmedRequest();

    /**
     * 确认 token（仅 pending 结果有值）
     */
    String pendingConfirmationToken();

    // ========== 工厂方法 ==========

    /**
     * 放行：继续执行下一个 Hook 或工具本身
     */
    static HookResult continueResult() {
        return Continue.INSTANCE;
    }

    /**
     * 阻断：终止 Hook 链，取消工具执行
     *
     * @param reason 阻断原因，会作为 ToolExecutionResultMessage 返回给 LLM
     */
    static HookResult block(String reason) {
        return new Block(reason);
    }

    /**
     * 修改参数后放行：替换工具的入参，继续执行
     *
     * @param newArgs 修改后的参数 Map
     */
    static HookResult modifyArgs(java.util.Map<String, Object> newArgs) {
        return new ModifyArgs(newArgs);
    }

    /**
     * 挂起等待用户确认：终止 Hook 链，状态机进入 pending_user_input。
     */
    static HookResult pending(String pendingId,
                              java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> questions,
                              dev.langchain4j.agent.tool.ToolExecutionRequest confirmedRequest) {
        return new Pending(pendingId, questions, confirmedRequest, null);
    }

    /**
     * 挂起等待用户确认，并保存确认 token。
     */
    static HookResult pending(String pendingId,
                              java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> questions,
                              dev.langchain4j.agent.tool.ToolExecutionRequest confirmedRequest,
                              String confirmationToken) {
        return new Pending(pendingId, questions, confirmedRequest, confirmationToken);
    }

    // ========== 内部实现 ==========

    /**
     * 放行结果（单例）
     */
    final class Continue implements HookResult {
        static final Continue INSTANCE = new Continue();

        private Continue() {}

        @Override public boolean isBlocked() { return false; }
        @Override public String blockReason() { return null; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return null; }
        @Override public String pendingId() { return null; }
        @Override public java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> pendingQuestions() { return null; }
        @Override public dev.langchain4j.agent.tool.ToolExecutionRequest pendingConfirmedRequest() { return null; }
        @Override public String pendingConfirmationToken() { return null; }

        @Override public String toString() { return "HookResult.continueResult()"; }
    }

    /**
     * 阻断结果
     */
    final class Block implements HookResult {
        private final String reason;

        Block(String reason) {
            this.reason = reason;
        }

        @Override public boolean isBlocked() { return true; }
        @Override public String blockReason() { return reason; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return null; }
        @Override public String pendingId() { return null; }
        @Override public java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> pendingQuestions() { return null; }
        @Override public dev.langchain4j.agent.tool.ToolExecutionRequest pendingConfirmedRequest() { return null; }
        @Override public String pendingConfirmationToken() { return null; }

        @Override public String toString() { return "HookResult.block(" + reason + ")"; }
    }

    /**
     * 修改参数结果
     */
    final class ModifyArgs implements HookResult {
        private final java.util.Map<String, Object> args;

        ModifyArgs(java.util.Map<String, Object> args) {
            this.args = args;
        }

        @Override public boolean isBlocked() { return false; }
        @Override public String blockReason() { return null; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return args; }
        @Override public String pendingId() { return null; }
        @Override public java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> pendingQuestions() { return null; }
        @Override public dev.langchain4j.agent.tool.ToolExecutionRequest pendingConfirmedRequest() { return null; }
        @Override public String pendingConfirmationToken() { return null; }

        @Override public String toString() { return "HookResult.modifyArgs(" + args + ")"; }
    }

    /**
     * 挂起结果
     */
    final class Pending implements HookResult {
        private final String pendingId;
        private final java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> questions;
        private final dev.langchain4j.agent.tool.ToolExecutionRequest confirmedRequest;
        private final String confirmationToken;

        Pending(String pendingId,
                java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> questions,
                dev.langchain4j.agent.tool.ToolExecutionRequest confirmedRequest,
                String confirmationToken) {
            this.pendingId = pendingId;
            this.questions = questions;
            this.confirmedRequest = confirmedRequest;
            this.confirmationToken = confirmationToken;
        }

        @Override public boolean isBlocked() { return true; }
        @Override public String blockReason() { return "等待用户确认"; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return null; }
        @Override public String pendingId() { return pendingId; }
        @Override public java.util.List<com.msz.resume.ai.chat.tooling.dto.QuestionDto> pendingQuestions() { return questions; }
        @Override public dev.langchain4j.agent.tool.ToolExecutionRequest pendingConfirmedRequest() { return confirmedRequest; }
        @Override public String pendingConfirmationToken() { return confirmationToken; }

        @Override public String toString() { return "HookResult.pending(" + pendingId + ")"; }
    }
}
