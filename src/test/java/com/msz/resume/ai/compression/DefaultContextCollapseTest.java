package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CollapseResult;
import com.msz.resume.ai.chat.session.entity.FoldedMessageBlob;
import com.msz.resume.ai.chat.session.service.FoldedMessageBlobService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultContextCollapse 单元测试
 *
 * <p>测试投影式折叠架构
 */
class DefaultContextCollapseTest {

    private JarvisCompressionProperties properties;
    private TestableFoldedMessageService foldedMessageService;
    private DefaultContextCollapse contextCollapse;

    @BeforeEach
    void setUp() {
        properties = new JarvisCompressionProperties();
        properties.setModelContextWindow(10000);

        foldedMessageService = new TestableFoldedMessageService();
        contextCollapse = new DefaultContextCollapse(properties, foldedMessageService);
    }

    @Test
    @DisplayName("recordCollapse() 消息太少时不折叠")
    void recordCollapse_whenTooFewMessages_shouldNotCollapse() {
        CollapseResult result = contextCollapse.recordCollapse(2, 100, "session1");

        assertFalse(result.wasCollapsed());
        assertEquals(0, result.messageCount());
    }

    @Test
    @DisplayName("recordCollapse() 消息足够时记录折叠状态")
    void recordCollapse_whenEnoughMessages_shouldRecord() {
        // 6 条消息，保留最近 4 条，折叠前 2 条
        CollapseResult result = contextCollapse.recordCollapse(6, 1000, "session1");

        assertTrue(result.wasCollapsed());
        assertEquals(2, result.messageCount());
        assertEquals(0, result.startIndex());
        assertEquals(2, result.endIndex());

        // 验证保存了折叠记录
        assertTrue(foldedMessageService.wasSaveCalled());
    }

    @Test
    @DisplayName("projectView() 无折叠记录时返回原始消息")
    void projectView_whenNoFoldedRecords_shouldReturnOriginal() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("Hello"),
                AiMessage.from("Hi")
        );

        List<ChatMessage> projected = contextCollapse.projectView(messages, "session1");

        assertEquals(messages, projected);
    }

    @Test
    @DisplayName("projectView() 有折叠记录时生成投影视图")
    void projectView_whenHasFoldedRecords_shouldProject() {
        // 先记录折叠
        foldedMessageService.addFoldedRecord("session1", 0, 2, 2, 100);

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            messages.add(UserMessage.from("Message " + i));
        }

        List<ChatMessage> projected = contextCollapse.projectView(messages, "session1");

        // 投影视图：[摘要] + [Msg2] [Msg3] [Msg4] [Msg5]
        assertEquals(5, projected.size());

        // 第一条是摘要消息
        assertTrue(projected.get(0).toString().contains("[早期对话已折叠]"));

        // 后面是未被折叠的消息
        assertTrue(projected.get(1).toString().contains("Message 2"));
        assertTrue(projected.get(4).toString().contains("Message 5"));
    }

    @Test
    @DisplayName("needsCollapse() 正确判断是否需要折叠")
    void needsCollapse_shouldReturnCorrectResult() {
        // 90% 阈值，10000 窗口
        assertFalse(contextCollapse.needsCollapse(8000));  // 80%
        assertFalse(contextCollapse.needsCollapse(9000));  // 90%
        assertTrue(contextCollapse.needsCollapse(9001));   // 90.01%
        assertTrue(contextCollapse.needsCollapse(9500));   // 95%
    }

    @Test
    @DisplayName("shouldBlockTools() 正确判断是否阻塞工具")
    void shouldBlockTools_shouldReturnCorrectResult() {
        // 95% 阻塞阈值
        assertFalse(contextCollapse.shouldBlockTools(9000));  // 90%
        assertFalse(contextCollapse.shouldBlockTools(9500));  // 95%
        assertTrue(contextCollapse.shouldBlockTools(9600));   // 96%
    }

    @Test
    @DisplayName("isActive() 正确检查折叠状态")
    void isActive_shouldCheckFoldedState() {
        foldedMessageService.setHasFoldedMessages(true);
        assertTrue(contextCollapse.isActive("session1"));

        foldedMessageService.setHasFoldedMessages(false);
        assertFalse(contextCollapse.isActive("session2"));
    }

    @Test
    @DisplayName("releaseFolded() 调用服务释放")
    void releaseFolded_shouldCallService() {
        // 先添加一条折叠记录
        foldedMessageService.addFoldedRecord("session1", 0, 2, 2, 100);

        int count = contextCollapse.releaseFolded("session1");

        assertEquals(1, count);
        assertTrue(foldedMessageService.wasReleaseCalled());
    }

    @Test
    @DisplayName("投影式折叠：原始消息不变，投影视图变化")
    void projectionMode_originalMessagesUnchanged() {
        // 记录折叠
        foldedMessageService.addFoldedRecord("session1", 0, 2, 2, 100);

        List<ChatMessage> original = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            original.add(UserMessage.from("Message " + i));
        }

        // 生成投影视图
        List<ChatMessage> projected = contextCollapse.projectView(original, "session1");

        // 原始消息不变
        assertEquals(6, original.size());
        assertEquals("Message 0", original.get(0).toString().replace("UserMessage { text = \"Message 0\" }", "Message 0").contains("Message 0") ? "Message 0" : original.get(0).toString());

        // 投影视图减少
        assertEquals(5, projected.size());
    }

    @Test
    @DisplayName("releaseFolded() 后投影视图恢复原始消息")
    void releaseFolded_thenProjectViewRestores() {
        // 记录折叠
        foldedMessageService.addFoldedRecord("session1", 0, 2, 2, 100);

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            messages.add(UserMessage.from("Message " + i));
        }

        // 投影视图是折叠的
        List<ChatMessage> projected1 = contextCollapse.projectView(messages, "session1");
        assertEquals(5, projected1.size());

        // 释放折叠
        foldedMessageService.setReleaseResult(1);
        contextCollapse.releaseFolded("session1");
        foldedMessageService.clearFoldedRecords();

        // 投影视图恢复原始
        List<ChatMessage> projected2 = contextCollapse.projectView(messages, "session1");
        assertEquals(6, projected2.size());
    }

    // ==================== 测试辅助类 ====================

    static class TestableFoldedMessageService extends FoldedMessageBlobService {
        private boolean saveCalled = false;
        private boolean releaseCalled = false;
        private boolean hasFoldedMessages = false;
        private int releaseResult = 0;
        private List<FoldedMessageBlob> foldedRecords = new ArrayList<>();

        public TestableFoldedMessageService() {
            super(null);
        }

        public void setHasFoldedMessages(boolean has) {
            this.hasFoldedMessages = has;
        }

        public void setReleaseResult(int result) {
            this.releaseResult = result;
        }

        public boolean wasSaveCalled() {
            return saveCalled;
        }

        public boolean wasReleaseCalled() {
            return releaseCalled;
        }

        public void addFoldedRecord(String sessionId, int startIndex, int endIndex, int messageCount, int tokensFolded) {
            hasFoldedMessages = true;
            foldedRecords.add(new FoldedMessageBlob(sessionId, foldedRecords.size() + 1, startIndex, endIndex, messageCount, tokensFolded));
        }

        public void clearFoldedRecords() {
            foldedRecords.clear();
            hasFoldedMessages = false;
        }

        @Override
        public int save(String sessionId, int startIndex, int endIndex, int messageCount, int tokensFolded) {
            saveCalled = true;
            addFoldedRecord(sessionId, startIndex, endIndex, messageCount, tokensFolded);
            return foldedRecords.size();
        }

        @Override
        public List<FoldedMessageBlob> getFoldedRecords(String sessionId) {
            return new ArrayList<>(foldedRecords);
        }

        @Override
        public int releaseLatestFoldGroup(String sessionId) {
            releaseCalled = true;
            if (!foldedRecords.isEmpty()) {
                foldedRecords.remove(foldedRecords.size() - 1);
                if (foldedRecords.isEmpty()) {
                    hasFoldedMessages = false;
                }
                return 1;
            }
            return 0;
        }

        @Override
        public boolean hasFoldedMessages(String sessionId) {
            return hasFoldedMessages;
        }

        @Override
        public int getFoldGroupCount(String sessionId) {
            return foldedRecords.size();
        }

        @Override
        public List<Integer> getFoldedIndices(String sessionId) {
            List<Integer> indices = new ArrayList<>();
            for (FoldedMessageBlob record : foldedRecords) {
                for (int i = record.getStartIndex(); i < record.getEndIndex(); i++) {
                    indices.add(i);
                }
            }
            return indices;
        }
    }
}
