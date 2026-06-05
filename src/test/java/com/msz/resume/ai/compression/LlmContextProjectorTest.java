package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.LlmContextCheckpoint;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LlmContextProjectorTest {

    private final LlmContextProjector projector = new LlmContextProjector();

    @Test
    @DisplayName("无 checkpoint 时返回完整历史")
    void projectWithoutCheckpointReturnsFullHistory() {
        List<ChatMessage> history = List.of(
                UserMessage.from("m1"),
                UserMessage.from("m2")
        );

        List<ChatMessage> projected = projector.project(history, null, "session1");

        assertSame(history, projected);
    }

    @Test
    @DisplayName("有 checkpoint 时返回摘要前缀加原始尾部")
    void projectWithCheckpointReturnsSummaryPlusTail() {
        List<ChatMessage> history = List.of(
                UserMessage.from("old-1"),
                UserMessage.from("old-2"),
                UserMessage.from("tail-1"),
                UserMessage.from("tail-2")
        );
        LlmContextCheckpoint checkpoint = new LlmContextCheckpoint(
                2,
                4,
                List.of(UserMessage.from("summary")),
                100,
                20
        );

        List<ChatMessage> projected = projector.project(history, checkpoint, "session1");

        assertEquals(3, projected.size());
        assertEquals("summary", ((UserMessage) projected.get(0)).singleText());
        assertEquals("tail-1", ((UserMessage) projected.get(1)).singleText());
        assertEquals("tail-2", ((UserMessage) projected.get(2)).singleText());
    }

    @Test
    @DisplayName("checkpoint 后追加的新消息包含在投影视图")
    void projectIncludesMessagesAddedAfterCheckpoint() {
        List<ChatMessage> history = List.of(
                UserMessage.from("old-1"),
                UserMessage.from("old-2"),
                UserMessage.from("tail-1"),
                UserMessage.from("new-user-message")
        );
        LlmContextCheckpoint checkpoint = new LlmContextCheckpoint(
                2,
                3,
                List.of(UserMessage.from("summary")),
                100,
                20
        );

        List<ChatMessage> projected = projector.project(history, checkpoint, "session1");

        assertEquals(3, projected.size());
        assertEquals("new-user-message", ((UserMessage) projected.get(2)).singleText());
    }

    @Test
    @DisplayName("tailStartIndex 越界时回退完整历史")
    void projectWhenTailStartOutOfRangeReturnsFullHistory() {
        List<ChatMessage> history = List.of(UserMessage.from("m1"));
        LlmContextCheckpoint checkpoint = new LlmContextCheckpoint(
                5,
                10,
                List.of(UserMessage.from("summary")),
                100,
                20
        );

        List<ChatMessage> projected = projector.project(history, checkpoint, "session1");

        assertSame(history, projected);
    }

    @Test
    @DisplayName("tailStartIndex 越界时投影元数据标记 checkpoint 未使用")
    void projectMetadataWhenTailStartOutOfRangeMarksCheckpointUnused() {
        List<ChatMessage> history = List.of(UserMessage.from("m1"));
        LlmContextCheckpoint checkpoint = new LlmContextCheckpoint(
                5,
                10,
                List.of(UserMessage.from("summary")),
                100,
                20
        );

        LlmContextProjector.Projection projection = projector.projectWithMetadata(history, checkpoint, "session1");

        assertSame(history, projection.messages());
        assertEquals(false, projection.checkpointApplied());
        assertEquals(0, projection.fullHistoryTailStart());
        assertEquals(0, projection.summaryPrefixSize());
    }
}
