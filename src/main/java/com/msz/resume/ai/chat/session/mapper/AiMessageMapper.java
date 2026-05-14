/**
 * AiMessage Mapper：ai_message 表的数据访问层
 * 继承 BaseMapper 后自动拥有基础 CRUD
 * 额外提供原生 SQL 方法处理批量插入和按会话查询
 */
package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.AiMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /**
     * 按会话ID查询所有消息，按 id 升序排列（保证消息顺序）
     */
    @Select("SELECT * FROM ai_message WHERE session_id = #{sessionId} ORDER BY id")
    List<AiMessage> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计指定会话的消息数量
     * 用于增量保存时判断已有消息数
     */
    @Select("SELECT COUNT(*) FROM ai_message WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 批量插入消息
     * 使用 MyBatis 动态 SQL 实现
     */
    @Insert("""
        <script>
        INSERT INTO ai_message (session_id, message_type, content, tool_calls_json, tool_result, tool_call_id, tool_name, token_count, is_compressed, created_at)
        VALUES
        <foreach collection="list" item="msg" separator=",">
            (#{msg.sessionId}, #{msg.messageType}, #{msg.content}, #{msg.toolCallsJson}, #{msg.toolResult}, #{msg.toolCallId}, #{msg.toolName}, #{msg.tokenCount}, #{msg.isCompressed}, #{msg.createdAt})
        </foreach>
        </script>
        """)
    void insertBatch(@Param("list") List<AiMessage> messages);
}
