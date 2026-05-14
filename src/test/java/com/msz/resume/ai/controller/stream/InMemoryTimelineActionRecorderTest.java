package com.msz.resume.ai.chat.runtime.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryTimelineActionRecorderTest {

    @Test
    @DisplayName("同一 action id 的 start/result 事件会稳定合并并保留首次顺序")
    void recordMergesEventsByStableId() {
        InMemoryTimelineActionRecorder recorder = new InMemoryTimelineActionRecorder();

        recorder.record("tool_use_started", 3, Map.of(
                "id", "tool_action_call_1",
                "toolName", "openviking_read",
                "status", "running",
                "title", "读取资源"
        ));
        recorder.record("tool_use_delta", 4, Map.of(
                "id", "tool_action_call_1",
                "toolName", "openviking_read",
                "status", "running",
                "summary", "正在读取资源内容"
        ));
        recorder.record("assistant_checkpoint", 2, Map.of(
                "id", "checkpoint_1",
                "title", "开始处理",
                "content", "先定位关键资源"
        ));
        recorder.record("tool_use_result", 5, Map.of(
                "id", "tool_action_call_1",
                "toolName", "openviking_read",
                "status", "success",
                "summary", "读取完成"
        ));
        recorder.record("run_step", 1, Map.of(
                "id", "debug_step",
                "status", "running"
        ));

        List<Map<String, Object>> actions = recorder.snapshot();

        assertEquals(2, actions.size());
        assertEquals("checkpoint_1", actions.get(0).get("id"));
        assertEquals("checkpoint", actions.get(0).get("kind"));
        assertEquals("tool_action_call_1", actions.get(1).get("id"));
        assertEquals("tool_use", actions.get(1).get("kind"));
        assertEquals("success", actions.get(1).get("status"));
        assertEquals("读取完成", actions.get(1).get("summary"));
        assertEquals(3L, actions.get(1).get("firstSequence"));
        assertEquals(5L, actions.get(1).get("sequence"));
    }

    @Test
    @DisplayName("AskUserQuestion pending 事件会持久化为用户补充信息动作并按 pendingId 合并")
    void recordUserQuestionEventsAsTimelineAction() {
        InMemoryTimelineActionRecorder recorder = new InMemoryTimelineActionRecorder();

        recorder.record("ask_user_question", 6, Map.of(
                "pendingId", "pending-1",
                "toolCallId", "call_question",
                "questions", List.of(Map.of("questionText", "你的目标岗位是什么？"))
        ));
        recorder.record("pending", 7, Map.of(
                "pendingId", "pending-1",
                "toolCallId", "call_question",
                "questions", List.of(Map.of("questionText", "你的目标岗位是什么？"))
        ));

        List<Map<String, Object>> actions = recorder.snapshot();

        assertEquals(1, actions.size());
        assertEquals("user_question_pending-1", actions.getFirst().get("id"));
        assertEquals("user_question", actions.getFirst().get("kind"));
        assertEquals("需要你补充信息", actions.getFirst().get("title"));
        assertEquals("你的目标岗位是什么？", actions.getFirst().get("summary"));
        assertEquals("pending", actions.getFirst().get("status"));
        assertEquals("pending-1", actions.getFirst().get("pendingId"));
        assertEquals("call_question", actions.getFirst().get("toolCallId"));
        assertEquals(false, actions.getFirst().get("promptVisible"));
        assertEquals(true, actions.getFirst().get("persistable"));
        assertEquals(false, actions.getFirst().get("sensitive"));
        assertEquals(1, actions.getFirst().get("questionCount"));
        assertEquals(6L, actions.getFirst().get("firstSequence"));
        assertEquals(7L, actions.getFirst().get("sequence"));
    }
}
