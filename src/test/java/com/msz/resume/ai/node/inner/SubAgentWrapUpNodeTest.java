package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.agent.SubAgentType;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentWrapUpNodeTest {

    @Test
    @DisplayName("首次进入强制收束应跳过未执行工具并额外给5轮")
    void firstWrapUpShouldSkipPendingToolsAndExtendFiveTurns() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("openviking_read")
                .arguments("{\"uri\":\"viking://demo\"}")
                .build();
        QueryLoopState state = new QueryLoopState(new HashMap<>(Map.of(
                QueryLoopState.MESSAGE_HISTORY, List.<ChatMessage>of(
                        UserMessage.from("explore"),
                        AiMessage.aiMessage(null, List.of(request))
                ),
                QueryLoopState.IS_SUB_AGENT, true,
                QueryLoopState.SUB_AGENT_TYPE, SubAgentType.ResumeBusinessExplore,
                QueryLoopState.TURN_COUNT, 50,
                QueryLoopState.MAX_TURNS, 50,
                QueryLoopState.SUB_AGENT_WRAP_UP_MAX_TURNS, 5
        )));

        Map<String, Object> update = new SubAgentWrapUpNode().apply(state).join();

        assertEquals(true, update.get(QueryLoopState.SUB_AGENT_WRAP_UP));
        assertEquals(55, update.get(QueryLoopState.MAX_TURNS));
        @SuppressWarnings("unchecked")
        List<ChatMessage> additions = (List<ChatMessage>) update.get(QueryLoopState.MESSAGE_HISTORY);
        assertEquals(2, additions.size());
        ToolExecutionResultMessage skipped = assertInstanceOf(ToolExecutionResultMessage.class, additions.get(0));
        assertEquals("call-1", skipped.id());
        assertTrue(skipped.text().contains("exploration turn budget"));
        assertInstanceOf(UserMessage.class, additions.get(1));
    }

    @Test
    @DisplayName("收束阶段再次请求工具不应继续延长截止轮次")
    void repeatedWrapUpShouldNotKeepExtendingMaxTurns() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-2")
                .name("openviking_grep")
                .arguments("{}")
                .build();
        QueryLoopState state = new QueryLoopState(new HashMap<>(Map.of(
                QueryLoopState.MESSAGE_HISTORY, List.<ChatMessage>of(AiMessage.aiMessage(null, List.of(request))),
                QueryLoopState.IS_SUB_AGENT, true,
                QueryLoopState.SUB_AGENT_WRAP_UP, true,
                QueryLoopState.TURN_COUNT, 52,
                QueryLoopState.MAX_TURNS, 55,
                QueryLoopState.SUB_AGENT_WRAP_UP_MAX_TURNS, 5
        )));

        Map<String, Object> update = new SubAgentWrapUpNode().apply(state).join();

        assertEquals(55, update.get(QueryLoopState.MAX_TURNS));
    }
}
