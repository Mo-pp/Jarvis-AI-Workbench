package com.msz.resume.ai.integrations.openviking.core.recall;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenVikingRecallEngineTest {

    @Test
    @DisplayName("短追问应结合上一轮用户主题触发 resource 召回")
    void shouldUseRecentUserContextForShortFollowUpRecall() {
        OpenVikingFindResponse.MatchedContext resource = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级未完成事项摘要",
                "docs",
                0.84,
                "Matches follow-up with Agent Loop context"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(resource), List.of(), 1),
                null,
                0.1
        ));
        client.overviewResponse = new OpenVikingReadResponse("ok", "Agent Loop 未完成工作 overview", null, 0.1);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下Agent Loop 架构升级了什么"),
                AiMessage.from("这里是上一轮回答"),
                UserMessage.from("有未完成的相关工作")
        ));

        assertEquals("injected", result.status());
        assertTrue(client.lastRequest.query().contains("Agent Loop 架构升级"));
        assertTrue(client.lastRequest.query().contains("有未完成的相关工作"));
        assertEquals("resource", result.candidates().get(0).type());
    }

    @Test
    @DisplayName("已注入过 overview 的 URI 不应重复注入")
    void shouldSuppressDuplicateUriWhenAlreadySurfacedAtSameOrDeeperLevel() {
        OpenVikingFindResponse.MatchedContext strong = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级文档摘要",
                "docs",
                0.86,
                "Matches Agent Loop 架构升级"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(strong), List.of(), 1),
                null,
                0.1
        ));
        client.overviewResponse = new OpenVikingReadResponse("ok", "Agent Loop overview details", null, 0.1);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of(
                QueryLoopState.SURFACED_OPENVIKING_URIS,
                Map.of("viking://resources/agent-loop/upgrade.md", "overview")
        )), List.of(UserMessage.from("讲一下 Agent Loop 架构升级了什么")));

        assertEquals("no_candidates", result.status());
        assertTrue(result.injectedContext().isBlank());
        assertEquals(1, result.suppressedCandidates().size());
        assertEquals("already_surfaced", result.suppressedCandidates().get(0).reason());
        assertEquals(Boolean.FALSE, result.toTraceMeta().get("injected"));
        assertEquals(1, result.toTraceMeta().get("suppressedCount"));
        assertEquals(0, client.overviewCalls);
    }

    @Test
    @DisplayName("已注入过摘要的 URI 可升级为 overview")
    void shouldUpgradePreviouslySurfacedAbstractToOverview() {
        OpenVikingFindResponse.MatchedContext strong = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级文档摘要",
                "docs",
                0.86,
                "Matches Agent Loop 架构升级"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(strong), List.of(), 1),
                null,
                0.1
        ));
        client.overviewResponse = new OpenVikingReadResponse("ok", "Agent Loop overview details", null, 0.1);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );
        QueryLoopState state = new QueryLoopState(Map.of(
                QueryLoopState.SURFACED_OPENVIKING_URIS,
                Map.of("viking://resources/agent-loop/upgrade.md", "abstract")
        ));

        OpenVikingRecallResult result = engine.prepare(state, List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));
        Map<String, String> merged = engine.mergeSurfacedUris(state, result);

        assertEquals("injected", result.status());
        assertTrue(result.injectedContext().contains("Overview:"));
        assertEquals("overview", result.surfacedUriLevels().get("viking://resources/agent-loop/upgrade.md"));
        assertEquals("overview", merged.get("viking://resources/agent-loop/upgrade.md"));
    }

    @Test
    @DisplayName("总注入预算不足时应压掉后续候选并写入 trace 元数据")
    void shouldSuppressCandidatesWhenTotalBudgetIsExceeded() {
        OpenVikingFindResponse.MatchedContext first = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/first.md",
                "resource",
                true,
                "A".repeat(300),
                "docs",
                0.7,
                "first"
        );
        OpenVikingFindResponse.MatchedContext second = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/second.md",
                "resource",
                true,
                "B".repeat(300),
                "docs",
                0.69,
                "second"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(first, second), List.of(), 2),
                null,
                0.1
        ));
        OpenVikingRecallProperties properties = enabledRetrievalProperties();
        properties.setMaxInjectedChars(700);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(properties, new OpenVikingRecallTriggerPolicy(), client);

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("总结一下这两个架构设计文档")
        ));

        assertEquals("injected", result.status());
        assertEquals(1, result.candidates().size());
        assertEquals(1, result.suppressedCandidates().size());
        assertEquals("budget_limit", result.suppressedCandidates().get(0).reason());
        assertTrue(result.budgetTruncated());
        assertEquals(Boolean.TRUE, result.toTraceMeta().get("budgetTruncated"));
    }

    @Test
    @DisplayName("不要查知识库只应跳过 resource，不应阻止 memory 召回")
    void shouldIgnoreResourceScopeWithoutBlockingMemoryRecall() {
        OpenVikingFindResponse.MatchedContext memory = new OpenVikingFindResponse.MatchedContext(
                "viking://user/u-1/memories/feedback_style.md",
                "memory",
                true,
                "用户喜欢直接、简洁的回答",
                "memory",
                0.82,
                "Matches previous preference"
        );
        ScopeAwareOpenVikingClient client = new ScopeAwareOpenVikingClient();
        client.memoryResponse = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(memory), List.of(), List.of(), 1),
                null,
                0.1
        );
        client.throwForResource = true;
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(stateWithIdentity(), List.of(
                UserMessage.from("不要查知识库，但记忆里我之前说过输出风格偏好吗")
        ));

        assertEquals("injected", result.status());
        assertEquals(1, result.candidates().size());
        assertEquals("memory", result.candidates().get(0).type());
        assertTrue(result.failedScopes().isEmpty());
    }

    @Test
    @DisplayName("混合 scope 中某个 scope 失败时应记录失败并保留成功候选")
    void shouldTraceFailedScopeAndInjectSuccessfulCandidate() {
        OpenVikingFindResponse.MatchedContext memory = new OpenVikingFindResponse.MatchedContext(
                "viking://user/u-1/memories/feedback_style.md",
                "memory",
                true,
                "用户喜欢直接、简洁的回答",
                "memory",
                0.82,
                "Matches previous preference"
        );
        ScopeAwareOpenVikingClient client = new ScopeAwareOpenVikingClient();
        client.memoryResponse = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(memory), List.of(), List.of(), 1),
                null,
                0.1
        );
        client.throwForResource = true;
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(stateWithIdentity(), List.of(
                UserMessage.from("Agent Loop 架构升级和我之前说过的输出风格一起看")
        ));

        assertEquals("injected", result.status());
        assertEquals(1, result.candidates().size());
        assertEquals("memory", result.candidates().get(0).type());
        assertEquals(1, result.failedScopes().size());
        assertEquals("resource", result.failedScopes().get(0).scope());
        assertEquals(1, result.toTraceMeta().get("failedScopeCount"));
    }

    @Test
    @DisplayName("检索开关开启时应查 resource 并注入摘要上下文")
    void shouldRetrieveResourceAndInjectContextWhenEnabled() {
        OpenVikingFindResponse.MatchedContext strong = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级文档摘要",
                "docs",
                0.86,
                "Matches Agent Loop 架构升级"
        );
        OpenVikingFindResponse.MatchedContext weak = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/other.md",
                "resource",
                true,
                "Unrelated",
                "docs",
                0.2,
                "weak"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(strong, weak), List.of(), 2),
                null,
                0.1
        ));
        client.overviewResponse = new OpenVikingReadResponse("ok", "Agent Loop overview details", null, 0.1);
        OpenVikingRecallProperties properties = enabledRetrievalProperties();
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(properties, new OpenVikingRecallTriggerPolicy(), client);

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));

        assertEquals("injected", result.status());
        assertEquals("viking://resources/", client.lastRequest.targetUri());
        assertEquals(1, result.candidates().size());
        assertTrue(result.injectedContext().contains("<system-reminder>"));
        assertTrue(result.injectedContext().contains("viking://resources/agent-loop/upgrade.md"));
        assertTrue(result.injectedContext().contains("Agent Loop 架构升级文档摘要"));
        assertTrue(result.injectedContext().contains("Agent Loop overview details"));
        assertEquals("read", result.candidates().get(0).overviewStatus());
        assertEquals("viking://resources/agent-loop/upgrade.md", client.lastOverviewUri);

        List<ChatMessage> injected = engine.inject(List.of(UserMessage.from("原始问题")), result);
        assertEquals(2, injected.size());
        assertTrue(((UserMessage) injected.get(0)).singleText().contains("OpenViking context"));
    }

    @Test
    @DisplayName("检索开关关闭时只触发不注入")
    void shouldNotRetrieveWhenRetrievalDisabled() {
        StubOpenVikingClient client = new StubOpenVikingClient(null);
        OpenVikingRecallProperties properties = new OpenVikingRecallProperties();
        properties.setRetrievalEnabled(false);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(properties, new OpenVikingRecallTriggerPolicy(), client);

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));

        assertEquals("triggered", result.status());
        assertTrue(result.shouldRecall());
        assertTrue(result.candidates().isEmpty());
        assertFalse(result.toTraceMeta().get("injected").equals(Boolean.TRUE));
        assertEquals(0, client.findCalls);
    }

    @Test
    @DisplayName("关闭 overview read 时高相关资源也只注入摘要")
    void shouldSkipOverviewWhenDisabled() {
        OpenVikingFindResponse.MatchedContext strong = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级文档摘要",
                "docs",
                0.86,
                "Matches Agent Loop 架构升级"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(strong), List.of(), 1),
                null,
                0.1
        ));
        OpenVikingRecallProperties properties = enabledRetrievalProperties();
        properties.setOverviewReadEnabled(false);
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(properties, new OpenVikingRecallTriggerPolicy(), client);

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));

        assertEquals("injected", result.status());
        assertEquals("not_applicable", result.candidates().get(0).overviewStatus());
        assertFalse(result.injectedContext().contains("Overview:"));
        assertEquals(0, client.overviewCalls);
    }

    @Test
    @DisplayName("overview 读取失败时应回退到摘要注入")
    void shouldFallbackToAbstractWhenOverviewReadFails() {
        OpenVikingFindResponse.MatchedContext strong = new OpenVikingFindResponse.MatchedContext(
                "viking://resources/agent-loop/upgrade.md",
                "resource",
                true,
                "Agent Loop 架构升级文档摘要",
                "docs",
                0.86,
                "Matches Agent Loop 架构升级"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(strong), List.of(), 1),
                null,
                0.1
        ));
        client.throwOnOverview = true;
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));

        assertEquals("injected", result.status());
        assertEquals("failed", result.candidates().get(0).overviewStatus());
        assertTrue(result.injectedContext().contains("Agent Loop 架构升级文档摘要"));
        assertFalse(result.injectedContext().contains("Overview:"));
    }

    @Test
    @DisplayName("记忆问题应查询 user memories scope 并注入 memory 候选")
    void shouldRetrieveMemoryScope() {
        OpenVikingFindResponse.MatchedContext memory = new OpenVikingFindResponse.MatchedContext(
                "viking://user/u-1/memories/feedback_style.md",
                "memory",
                true,
                "用户喜欢直接、简洁的回答",
                "memory",
                0.82,
                "Matches previous preference"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(memory), List.of(), List.of(), 1),
                null,
                0.1
        ));
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(stateWithIdentity(), List.of(
                UserMessage.from("我之前说过输出风格偏好吗")
        ));

        assertEquals("injected", result.status());
        assertEquals("viking://user/u-1/memories/", client.lastRequest.targetUri());
        assertEquals("memory", result.candidates().get(0).type());
        assertTrue(result.injectedContext().contains("Type: memory"));
        assertTrue(result.injectedContext().contains("用户喜欢直接、简洁的回答"));
    }

    @Test
    @DisplayName("skill 问题应查询 agent skills scope 并注入 skill 候选")
    void shouldRetrieveSkillScope() {
        OpenVikingFindResponse.MatchedContext skill = new OpenVikingFindResponse.MatchedContext(
                "viking://agent/jarvis/skills/architecture-review/SKILL.md",
                "skill",
                true,
                "用于架构分析和重构建议的 skill",
                "skill",
                0.79,
                "Matches architecture analysis skill"
        );
        StubOpenVikingClient client = new StubOpenVikingClient(new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(), List.of(skill), 1),
                null,
                0.1
        ));
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(stateWithIdentity(), List.of(
                UserMessage.from("有没有适合做架构分析的 skill")
        ));

        assertEquals("injected", result.status());
        assertEquals("viking://agent/jarvis/skills/", client.lastRequest.targetUri());
        assertEquals("skill", result.candidates().get(0).type());
        assertTrue(result.injectedContext().contains("Type: skill"));
        assertTrue(result.injectedContext().contains("architecture-review"));
    }

    @Test
    @DisplayName("OV 检索失败时应返回 failed 结果而不抛出")
    void shouldReturnFailedResultWhenOpenVikingThrows() {
        ErrorOpenVikingClient client = new ErrorOpenVikingClient();
        OpenVikingRecallEngine engine = new OpenVikingRecallEngine(
                enabledRetrievalProperties(),
                new OpenVikingRecallTriggerPolicy(),
                client
        );

        OpenVikingRecallResult result = engine.prepare(new QueryLoopState(Map.of()), List.of(
                UserMessage.from("讲一下 Agent Loop 架构升级了什么")
        ));

        assertEquals("failed", result.status());
        assertEquals("retrieval_failed", result.reason());
        assertTrue(result.injectedContext().isBlank());
    }

    private static OpenVikingRecallProperties enabledRetrievalProperties() {
        OpenVikingRecallProperties properties = new OpenVikingRecallProperties();
        properties.setRetrievalEnabled(true);
        properties.setMediumRelevanceThreshold(0.55);
        properties.setHighRelevanceThreshold(0.75);
        return properties;
    }

    private static QueryLoopState stateWithIdentity() {
        return new QueryLoopState(Map.of(
                QueryLoopState.OPENVIKING_IDENTITY,
                new OpenVikingIdentity("acct", "u-1", "jarvis")
        ));
    }

    private static class StubOpenVikingClient extends OpenVikingClient {
        private final OpenVikingFindResponse response;
        private OpenVikingFindRequest lastRequest;
        private OpenVikingReadResponse overviewResponse;
        private String lastOverviewUri;
        private int findCalls;
        private int overviewCalls;
        private boolean throwOnOverview;

        StubOpenVikingClient(OpenVikingFindResponse response) {
            super(new OpenVikingProperties());
            this.response = response;
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request) {
            this.findCalls++;
            this.lastRequest = request;
            return response;
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request, com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity identity) {
            return find(request);
        }

        @Override
        public OpenVikingReadResponse readOverview(String uri) {
            this.overviewCalls++;
            this.lastOverviewUri = uri;
            if (throwOnOverview) {
                throw new OpenVikingClientException("overview failed");
            }
            return overviewResponse;
        }
    }

    private static class ErrorOpenVikingClient extends OpenVikingClient {
        ErrorOpenVikingClient() {
            super(new OpenVikingProperties());
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request, com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity identity) {
            throw new OpenVikingClientException("boom");
        }
    }

    private static class ScopeAwareOpenVikingClient extends OpenVikingClient {
        private OpenVikingFindResponse memoryResponse;
        private boolean throwForResource;

        ScopeAwareOpenVikingClient() {
            super(new OpenVikingProperties());
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request, com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity identity) {
            if ("viking://resources/".equals(request.targetUri()) && throwForResource) {
                throw new OpenVikingClientException("resource failed");
            }
            if (request.targetUri() != null && request.targetUri().contains("/memories/")) {
                return memoryResponse;
            }
            return new OpenVikingFindResponse(
                    "ok",
                    new OpenVikingFindResponse.Result(List.of(), List.of(), List.of(), 0),
                    null,
                    0.1
            );
        }
    }
}
