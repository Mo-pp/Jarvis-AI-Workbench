package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.FoldedMessageBlob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 折叠状态 Mapper
 */
@Mapper
public interface FoldedMessageBlobMapper extends BaseMapper<FoldedMessageBlob> {

    /**
     * 查询会话的所有折叠记录
     */
    @Select("SELECT * FROM folded_message_blob WHERE session_id = #{sessionId} ORDER BY fold_group_id")
    List<FoldedMessageBlob> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询会话的最大折叠组ID
     */
    @Select("SELECT MAX(fold_group_id) FROM folded_message_blob WHERE session_id = #{sessionId}")
    Integer findMaxFoldGroupId(@Param("sessionId") String sessionId);

    /**
     * 删除会话的最新折叠组
     */
    @Select("DELETE FROM folded_message_blob WHERE session_id = #{sessionId} AND fold_group_id = " +
            "(SELECT MAX(fold_group_id) FROM (SELECT fold_group_id FROM folded_message_blob WHERE session_id = #{sessionId}) AS tmp)")
    int deleteLatestFoldGroup(@Param("sessionId") String sessionId);

    /**
     * 统计会话的折叠组数量
     */
    @Select("SELECT COUNT(DISTINCT fold_group_id) FROM folded_message_blob WHERE session_id = #{sessionId}")
    int countFoldGroups(@Param("sessionId") String sessionId);
}
