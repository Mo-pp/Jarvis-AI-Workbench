package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.chat.compression.LlmContextProjector;
import com.msz.resume.ai.chat.compression.model.LlmContextCheckpoint;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallLlmNodeCheckpointRemapTest {

    @Test
    @DisplayName("二次 L5 保留旧摘要前缀的一部分时 checkpoint 不丢恢复消息")
    void remapCheckpointPreservesUncompressedPrefixMessages() throws Exception {
        CallLlmNode node = new CallLlmNode(
                null, Optional.empty(), null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
        List<ChatMessage> projectedMessages = List.of(
                UserMessage.from("old-summary"),
                UserMessage.from("old-restored-plan"),
                UserMessage.from("tail-1"),
                UserMessage.from("tail-2")
        );
        LlmContextProjector.Projection projection = new LlmContextProjector.Projection(
                projectedMessages,
                true,
                10,
                2
        );
        LlmContextCheckpoint pipelineCheckpoint = new LlmContextCheckpoint(
                1,
                4,
                List.of(UserMessage.from("new-summary")),
                90000,
                5000
        );

        LlmContextCheckpoint remapped = invokeRemap(node, pipelineCheckpoint, projection, 12);

        assertEquals(10, remapped.tailStartIndex());
        assertEquals(12, remapped.sourceMessageCount());
        assertEquals(2, remapped.summaryMessages().size());
        assertEquals("new-summary", ((UserMessage) remapped.summaryMessages().get(0)).singleText());
        assertEquals("old-restored-plan", ((UserMessage) remapped.summaryMessages().get(1)).singleText());
    }

    private LlmContextCheckpoint invokeRemap(CallLlmNode node,
                                            LlmContextCheckpoint checkpoint,
                                            LlmContextProjector.Projection projection,
                                            int fullHistorySize) throws Exception {
        Method method = CallLlmNode.class.getDeclaredMethod(
                "remapCheckpoint",
                LlmContextCheckpoint.class,
                LlmContextProjector.Projection.class,
                int.class
        );
        method.setAccessible(true);
        return (LlmContextCheckpoint) method.invoke(node, checkpoint, projection, fullHistorySize);
    }

}
