package com.msz.resume.ai.chat.session.service;

import com.msz.resume.ai.chat.session.entity.FoldedMessageBlob;
import com.msz.resume.ai.chat.session.mapper.FoldedMessageBlobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 折叠状态服务
 *
 * <p>负责 L4 ContextCollapse 折叠状态的存储和查询。
 *
 * <p>架构说明：
 * <ul>
 *   <li>只存储折叠的元数据（消息索引范围），不存储消息内容</li>
 *   <li>原始消息始终保留在状态中</li>
 *   <li>PTL 恢复时删除折叠记录即可</li>
 * </ul>
 */
@Slf4j
@Service
public class FoldedMessageBlobService {

    private final FoldedMessageBlobMapper mapper;

    public FoldedMessageBlobService(FoldedMessageBlobMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存折叠记录
     *
     * @param sessionId     会话ID
     * @param startIndex    起始消息索引（包含）
     * @param endIndex      结束消息索引（不包含）
     * @param messageCount  折叠的消息数量
     * @param tokensFolded  折叠的 Token 数
     * @return 折叠组ID
     */
    @Transactional
    public int save(String sessionId, int startIndex, int endIndex, int messageCount, int tokensFolded) {
        // 获取下一个折叠组ID
        Integer maxGroupId = mapper.findMaxFoldGroupId(sessionId);
        int foldGroupId = (maxGroupId == null ? 0 : maxGroupId) + 1;

        FoldedMessageBlob blob = new FoldedMessageBlob(
                sessionId,
                foldGroupId,
                startIndex,
                endIndex,
                messageCount,
                tokensFolded
        );

        mapper.insert(blob);

        log.info("[FoldedMessageBlobService] 保存折叠记录: sessionId={}, foldGroupId={}, range=[{}, {}), count={}",
                sessionId, foldGroupId, startIndex, endIndex, messageCount);

        return foldGroupId;
    }

    /**
     * 获取会话的所有折叠记录
     *
     * @param sessionId 会话ID
     * @return 折叠记录列表
     */
    public List<FoldedMessageBlob> getFoldedRecords(String sessionId) {
        return mapper.findBySessionId(sessionId);
    }

    /**
     * 释放最新的折叠组
     *
     * @param sessionId 会话ID
     * @return 释放的记录数
     */
    @Transactional
    public int releaseLatestFoldGroup(String sessionId) {
        int count = mapper.deleteLatestFoldGroup(sessionId);
        log.info("[FoldedMessageBlobService] 释放折叠组: sessionId={}, count={}", sessionId, count);
        return count;
    }

    /**
     * 检查会话是否有折叠记录
     *
     * @param sessionId 会话ID
     * @return 是否有折叠记录
     */
    public boolean hasFoldedMessages(String sessionId) {
        return mapper.countFoldGroups(sessionId) > 0;
    }

    /**
     * 获取会话的折叠组数量
     *
     * @param sessionId 会话ID
     * @return 折叠组数量
     */
    public int getFoldGroupCount(String sessionId) {
        return mapper.countFoldGroups(sessionId);
    }

    /**
     * 获取所有被折叠的消息索引集合
     *
     * <p>用于生成投影视图时判断哪些消息需要被折叠。
     *
     * @param sessionId 会话ID
     * @return 被折叠的消息索引列表（升序）
     */
    public List<Integer> getFoldedIndices(String sessionId) {
        List<FoldedMessageBlob> records = mapper.findBySessionId(sessionId);
        List<Integer> indices = new ArrayList<>();

        for (FoldedMessageBlob record : records) {
            for (int i = record.getStartIndex(); i < record.getEndIndex(); i++) {
                indices.add(i);
            }
        }

        return indices;
    }
}
