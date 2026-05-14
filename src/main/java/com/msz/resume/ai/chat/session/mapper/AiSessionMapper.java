/**
 * AiSession Mapper：ai_session 表的数据访问层
 * 继承 BaseMapper 后自动拥有 insert/delete/updateById/selectById 等方法
 * 额外提供原生 SQL 方法处理 upsert 和查询活跃会话
 */
package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.AiSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiSessionMapper extends BaseMapper<AiSession> {

    /**
     * 插入或更新会话
     * 如果 session_id 已存在则更新，否则插入
     * 注意：方法名改为 upsert 避免与 BaseMapper.insertOrUpdate 冲突
     */
    @Insert("""
        INSERT INTO ai_session (session_id, owner_username, title, status, pinned, total_input_tokens, total_output_tokens, created_at, last_active_at, pinned_at)
        VALUES (#{sessionId}, #{ownerUsername}, #{title}, #{status}, #{pinned}, #{totalInputTokens}, #{totalOutputTokens}, #{createdAt}, #{lastActiveAt}, #{pinnedAt})
        ON DUPLICATE KEY UPDATE
            owner_username = VALUES(owner_username),
            status = VALUES(status),
            total_input_tokens = VALUES(total_input_tokens),
            total_output_tokens = VALUES(total_output_tokens),
            last_active_at = VALUES(last_active_at)
        """)
    void upsert(AiSession session);

    /**
     * 查询所有活跃会话
     */
    @Select("""
        SELECT * FROM ai_session
        WHERE owner_username = #{ownerUsername}
          AND status = 'active'
        ORDER BY pinned DESC, pinned_at DESC, last_active_at DESC
        """)
    List<AiSession> selectActiveSessions(@Param("ownerUsername") String ownerUsername);

    /** 更新会话标题 */
    @Update("""
        UPDATE ai_session
        SET title = #{title}
        WHERE session_id = #{sessionId}
          AND owner_username = #{ownerUsername}
          AND status = 'active'
        """)
    int updateTitle(@Param("sessionId") String sessionId,
                    @Param("ownerUsername") String ownerUsername,
                    @Param("title") String title);

    /** 更新会话置顶状态，置顶时记录时间 */
    @Update("""
        UPDATE ai_session
        SET pinned = #{pinned},
            pinned_at = CASE WHEN #{pinned} THEN NOW() ELSE NULL END
        WHERE session_id = #{sessionId}
          AND owner_username = #{ownerUsername}
          AND status = 'active'
        """)
    int updatePinned(@Param("sessionId") String sessionId,
                     @Param("ownerUsername") String ownerUsername,
                     @Param("pinned") boolean pinned);
}
