package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.ToolResultBlob;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ToolResultBlob Mapper：工具结果大对象的数据访问层
 */
@Mapper
public interface ToolResultBlobMapper extends BaseMapper<ToolResultBlob> {

    /**
     * 按会话ID查询所有工具结果
     */
    @Select("SELECT id, session_id, tool_name, tool_call_id, LEFT(content, 500) as content, original_size, created_at FROM tool_result_blob WHERE session_id = #{sessionId} ORDER BY id")
    List<ToolResultBlob> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 按ID查询完整内容
     */
    @Select("SELECT * FROM tool_result_blob WHERE id = #{id}")
    ToolResultBlob selectById(@Param("id") Long id);

    /**
     * 按会话ID删除所有工具结果（会话关闭时清理）
     */
    @Delete("DELETE FROM tool_result_blob WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计指定会话的工具结果数量
     */
    @Select("SELECT COUNT(*) FROM tool_result_blob WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);
}
