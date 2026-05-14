package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CollapseResult;
import com.msz.resume.ai.chat.session.entity.FoldedMessageBlob;
import com.msz.resume.ai.chat.session.service.FoldedMessageBlobService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认上下文折叠器实现（投影式折叠）
 *
 * <p>核心设计：
 * <ul>
 *   <li>collapse() 只记录折叠状态到数据库，不修改消息</li>
 *   <li>projectView() 根据折叠状态生成投影视图</li>
 *   <li>原始消息始终保留在状态中</li>
 * </ul>
 *
 * <p>触发条件：
 * <ul>
 *   <li>上下文利用率 > 90%: 启动折叠</li>
 *   <li>上下文利用率 > 95%: 阻塞工具调用</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultContextCollapse implements ContextCollapse {

    /** 保留最近的消息数量 */
    private static final int KEEP_RECENT = 4;

    private final JarvisCompressionProperties properties;
    private final FoldedMessageBlobService foldedMessageService;

    public DefaultContextCollapse(JarvisCompressionProperties properties,
                                   FoldedMessageBlobService foldedMessageService) {
        this.properties = properties;
        this.foldedMessageService = foldedMessageService;
    }

    @Override
    public CollapseResult recordCollapse(int messageCount, int tokens, String sessionId) {
        // 判断是否需要折叠
        if (messageCount <= KEEP_RECENT) {
            log.debug("[ContextCollapse] 消息太少，不需要折叠: count={}", messageCount);
            return CollapseResult.notCollapsed();
        }

        // 计算需要折叠的消息范围
        int startIndex = 0;
        int endIndex = messageCount - KEEP_RECENT;
        int foldedCount = endIndex - startIndex;

        if (foldedCount <= 0) {
            return CollapseResult.notCollapsed();
        }

        // 估算折叠节省的 Token 数
        // 假设折叠部分占总 Token 的比例与消息数量比例相同
        int tokensFolded = (int) (tokens * ((double) foldedCount / messageCount));

        // 保存折叠记录到数据库
        int foldGroupId = foldedMessageService.save(sessionId, startIndex, endIndex, foldedCount, tokensFolded);

        log.info("[ContextCollapse] 记录折叠: sessionId={}, foldGroupId={}, range=[{}, {}), count={}, tokens={}",
                sessionId, foldGroupId, startIndex, endIndex, foldedCount, tokensFolded);

        return new CollapseResult(foldGroupId, startIndex, endIndex, foldedCount, tokensFolded, tokens);
    }

    @Override
    public List<ChatMessage> projectView(List<ChatMessage> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return messages != null ? messages : List.of();
        }

        // 获取所有被折叠的消息索引
        List<Integer> foldedIndices = foldedMessageService.getFoldedIndices(sessionId);

        if (foldedIndices.isEmpty()) {
            // 没有折叠记录，返回原始消息
            return messages;
        }

        // 获取折叠记录用于生成摘要
        List<FoldedMessageBlob> records = foldedMessageService.getFoldedRecords(sessionId);

        // 构建投影视图
        List<ChatMessage> projected = new ArrayList<>();
        Set<Integer> foldedSet = new HashSet<>(foldedIndices);
        int lastFoldedEndIndex = -1;

        for (int i = 0; i < messages.size(); i++) {
            if (foldedSet.contains(i)) {
                // 这条消息被折叠了
                // 检查是否是折叠组的开始
                for (FoldedMessageBlob record : records) {
                    if (record.getStartIndex() == i && record.getEndIndex() > lastFoldedEndIndex) {
                        // 插入折叠摘要
                        projected.add(UserMessage.from(buildFoldedSummary(record)));
                        lastFoldedEndIndex = record.getEndIndex();
                    }
                }
                // 跳过被折叠的消息
            } else {
                // 这条消息没有被折叠，原样保留
                projected.add(messages.get(i));
            }
        }

        log.info("[ContextCollapse] 生成投影视图: sessionId={}, original={}, projected={}, folded={}",
                sessionId, messages.size(), projected.size(), foldedIndices.size());

        return projected;
    }

    @Override
    public int releaseFolded(String sessionId) {
        int count = foldedMessageService.releaseLatestFoldGroup(sessionId);
        if (count > 0) {
            log.info("[ContextCollapse] 释放折叠组: sessionId={}, count={}", sessionId, count);
        }
        return count;
    }

    @Override
    public boolean needsCollapse(int tokens) {
        double utilization = (double) tokens / properties.getModelContextWindow();
        return utilization > 0.90; // 90% 启动阈值
    }

    @Override
    public boolean shouldBlockTools(int tokens) {
        double utilization = (double) tokens / properties.getModelContextWindow();
        return utilization > 0.95; // 95% 阻塞阈值
    }

    @Override
    public boolean isActive(String sessionId) {
        return foldedMessageService.hasFoldedMessages(sessionId);
    }

    @Override
    public int getFoldGroupCount(String sessionId) {
        return foldedMessageService.getFoldGroupCount(sessionId);
    }

    /**
     * 构建折叠摘要消息
     *
     * @param record 折叠记录
     * @return 摘要消息
     */
    private String buildFoldedSummary(FoldedMessageBlob record) {
        StringBuilder sb = new StringBuilder();
        sb.append(FOLDED_SUMMARY_PREFIX).append("\n\n");
        sb.append("折叠了 ").append(record.getMessageCount()).append(" 条消息");
        sb.append("，节省约 ").append(record.getTokensFolded()).append(" tokens。\n");
        sb.append("如需查看完整历史，请告知。");

        return sb.toString();
    }
}
