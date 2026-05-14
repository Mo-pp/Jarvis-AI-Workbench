package com.msz.resume.ai.chat.api.dto;

import com.msz.resume.ai.chat.tooling.dto.UserAnswerDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户答案请求 DTO
 *
 * <p>前端调用 POST /api/chat/answer 时发送的请求体。
 *
 * <h2>请求示例</h2>
 * <pre>{@code
 * {
 *   "pendingId": "uuid-xxx",
 *   "answers": [
 *     {
 *       "questionId": "q1",
 *       "selectedOptionIds": ["opt1", "opt2"],
 *       "customInput": null,
 *       "notes": "我选择这两个选项"
 *     },
 *     {
 *       "questionId": "q2",
 *       "selectedOptionIds": [],
 *       "customInput": "这是我的自定义输入",
 *       "notes": null
 *     }
 *   ]
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnswerRequest {

    /**
     * 挂起会话ID
     * 对应 ChatResponse.pendingId
     */
    private String pendingId;

    /**
     * 用户答案列表
     * 每个问题对应一个 UserAnswerDto
     */
    private List<UserAnswerDto> answers;
}
