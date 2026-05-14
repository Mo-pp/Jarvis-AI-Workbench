package com.msz.resume.ai.chat.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TimelineActionRecordMapper extends BaseMapper<TimelineActionRecord> {

    @Select("""
        SELECT *
        FROM ai_timeline_action
        WHERE session_id = #{sessionId}
        ORDER BY anchor_message_index, first_sequence, sequence, id
        """)
    List<TimelineActionRecord> selectBySessionId(@Param("sessionId") String sessionId);

    @Select("""
        <script>
        SELECT *
        FROM ai_timeline_action
        WHERE session_id = #{sessionId}
          AND (sequence IS NOT NULL AND sequence > #{lastSequence})
          AND (persistable IS NULL OR persistable = TRUE)
          <if test="runId != null and runId != ''">
          AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.runId')) = #{runId}
          </if>
        ORDER BY sequence, id
        LIMIT #{limit}
        </script>
        """)
    List<TimelineActionRecord> selectReplayBySessionId(@Param("sessionId") String sessionId,
                                                       @Param("runId") String runId,
                                                       @Param("lastSequence") long lastSequence,
                                                       @Param("limit") int limit);

    @Delete("""
        DELETE FROM ai_timeline_action
        WHERE session_id = #{sessionId}
          AND anchor_message_index = -1
          AND kind = 'user_question'
          AND status = 'pending'
        """)
    int deleteUiOnlyPendingBySessionId(@Param("sessionId") String sessionId);

    @Insert("""
        <script>
        INSERT INTO ai_timeline_action (
            session_id,
            action_id,
            anchor_message_index,
            event_type,
            kind,
            first_sequence,
            sequence,
            status,
            payload_json,
            prompt_visible,
            persistable,
            created_at,
            updated_at
        )
        VALUES
        <foreach collection="list" item="action" separator=",">
            (
                #{action.sessionId},
                #{action.actionId},
                #{action.anchorMessageIndex},
                #{action.eventType},
                #{action.kind},
                #{action.firstSequence},
                #{action.sequence},
                #{action.status},
                #{action.payloadJson},
                #{action.promptVisible},
                #{action.persistable},
                #{action.createdAt},
                #{action.updatedAt}
            )
        </foreach>
        ON DUPLICATE KEY UPDATE
            anchor_message_index = IF(VALUES(anchor_message_index) = -1 AND anchor_message_index != -1,
                anchor_message_index,
                VALUES(anchor_message_index)),
            event_type = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(event_type),
                event_type),
            kind = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(kind),
                kind),
            first_sequence = CASE
                WHEN first_sequence IS NULL THEN VALUES(first_sequence)
                WHEN VALUES(first_sequence) IS NULL THEN first_sequence
                ELSE LEAST(first_sequence, VALUES(first_sequence))
            END,
            status = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(status),
                status),
            payload_json = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(payload_json),
                payload_json),
            prompt_visible = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(prompt_visible),
                prompt_visible),
            persistable = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(persistable),
                persistable),
            updated_at = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(updated_at),
                updated_at),
            sequence = IF(sequence IS NULL OR VALUES(sequence) IS NULL OR VALUES(sequence) >= sequence,
                VALUES(sequence),
                sequence)
        </script>
        """)
    void upsertBatch(@Param("list") List<TimelineActionRecord> actions);
}
