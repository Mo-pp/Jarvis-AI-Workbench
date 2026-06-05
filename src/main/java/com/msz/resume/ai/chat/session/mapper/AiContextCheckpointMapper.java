package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.AiContextCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiContextCheckpointMapper extends BaseMapper<AiContextCheckpoint> {

    @Select("SELECT * FROM ai_context_checkpoint WHERE session_id = #{sessionId}")
    AiContextCheckpoint selectBySessionId(@Param("sessionId") String sessionId);

    @Insert("""
        INSERT INTO ai_context_checkpoint
            (session_id, tail_start_index, source_message_count, summary_messages_json, original_tokens, compacted_tokens, created_at, updated_at)
        VALUES
            (#{checkpoint.sessionId}, #{checkpoint.tailStartIndex}, #{checkpoint.sourceMessageCount}, #{checkpoint.summaryMessagesJson}, #{checkpoint.originalTokens}, #{checkpoint.compactedTokens}, #{checkpoint.createdAt}, #{checkpoint.updatedAt})
        ON DUPLICATE KEY UPDATE
            tail_start_index = VALUES(tail_start_index),
            source_message_count = VALUES(source_message_count),
            summary_messages_json = VALUES(summary_messages_json),
            original_tokens = VALUES(original_tokens),
            compacted_tokens = VALUES(compacted_tokens),
            updated_at = VALUES(updated_at)
        """)
    void upsert(@Param("checkpoint") AiContextCheckpoint checkpoint);
}
