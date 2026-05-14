package com.msz.resume.ai.chat.session.service;

import com.msz.resume.ai.chat.session.entity.ToolResultBlob;
import com.msz.resume.ai.chat.session.mapper.ToolResultBlobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工具结果大对象存储服务
 *
 * <p>负责存储和检索超限的工具执行结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolResultBlobService {

    private final ToolResultBlobMapper mapper;

    /**
     * 存储工具结果
     *
     * @param sessionId 会话ID
     * @param toolName 工具名称
     * @param toolCallId 工具调用ID
     * @param content 完整内容
     * @param originalSize 原始大小
     * @return 存储记录的ID
     */
    @Transactional
    public Long save(String sessionId, String toolName, String toolCallId, String content, int originalSize) {
        ToolResultBlob blob = ToolResultBlob.of(sessionId, toolName, toolCallId, content, originalSize);
        mapper.insert(blob);

        log.debug("[ToolResultBlobService] 已存储工具结果: sessionId={}, toolName={}, id={}",
                sessionId, toolName, blob.getId());

        return blob.getId();
    }

    /**
     * 根据ID获取完整内容
     *
     * @param id 记录ID
     * @return 工具结果记录，不存在返回null
     */
    public ToolResultBlob getById(Long id) {
        if (id == null) {
            return null;
        }
        return mapper.selectById(id);
    }

    /**
     * 根据会话ID获取所有工具结果（不含完整内容，只有摘要）
     *
     * @param sessionId 会话ID
     * @return 工具结果列表
     */
    public List<ToolResultBlob> listBySessionId(String sessionId) {
        return mapper.selectBySessionId(sessionId);
    }

    /**
     * 删除会话的所有工具结果
     *
     * @param sessionId 会话ID
     * @return 删除的记录数
     */
    @Transactional
    public int deleteBySessionId(String sessionId) {
        int count = mapper.deleteBySessionId(sessionId);
        log.debug("[ToolResultBlobService] 已删除会话 {} 的 {} 条工具结果", sessionId, count);
        return count;
    }

    /**
     * 统计会话的工具结果数量
     *
     * @param sessionId 会话ID
     * @return 记录数量
     */
    public int countBySessionId(String sessionId) {
        return mapper.countBySessionId(sessionId);
    }
}
