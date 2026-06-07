package com.msz.resume.ai.resume.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.resume.evaluation.entity.ResumeEvaluationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeEvaluationJobMapper extends BaseMapper<ResumeEvaluationJob> {

    @Select("""
        SELECT *
        FROM resume_evaluation_job
        WHERE job_id = #{jobId}
        LIMIT 1
        """)
    ResumeEvaluationJob selectByJobId(@Param("jobId") String jobId);

    @Select("""
        SELECT *
        FROM resume_evaluation_job
        WHERE session_id = #{sessionId}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    ResumeEvaluationJob selectLatestBySessionId(@Param("sessionId") String sessionId);

    @Update("""
        UPDATE resume_evaluation_job
        SET status = 'running',
            updated_at = NOW()
        WHERE job_id = #{jobId}
          AND status = 'pending'
        """)
    int markRunning(@Param("jobId") String jobId);

    @Update("""
        UPDATE resume_evaluation_job
        SET status = 'success',
            result_json = #{resultJson},
            error_message = NULL,
            updated_at = NOW(),
            completed_at = NOW()
        WHERE job_id = #{jobId}
        """)
    int markSuccess(@Param("jobId") String jobId, @Param("resultJson") String resultJson);

    @Update("""
        UPDATE resume_evaluation_job
        SET status = 'failed',
            error_message = #{errorMessage},
            updated_at = NOW(),
            completed_at = NOW()
        WHERE job_id = #{jobId}
        """)
    int markFailed(@Param("jobId") String jobId, @Param("errorMessage") String errorMessage);
}
