package com.msz.resume.ai.resume.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.ResumeVO;
import com.msz.resume.ai.resume.evaluation.dto.BatchResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.CandidateResumeEvaluationCard;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultResumeEvaluationServiceTest {

    @Test
    @DisplayName("无 JD 评分不会返回 jdMatch")
    void evaluateWithoutJdShouldNotReturnJdMatch() {
        DefaultResumeEvaluationService service = new DefaultResumeEvaluationService(
                new FakeChatModel("""
                        {
                          "originalResume": {"score": 61, "summary": "原始简历一般"},
                          "generatedResume": {"score": 82, "summary": "预览简历较好"},
                          "quality": {"score": 82, "summary": "预览简历较好"},
                          "hasJd": false
                        }
                        """),
                new ObjectMapper()
        );

        ResumeEvaluationBundle result = service.evaluateWithoutJd(ResumeEvaluationRequest.builder()
                .originalResumeText("本科 Java 后端，做过缓存优化。")
                .generatedResume(ResumeVO.builder().summary("通过缓存优化将接口延迟降低 30%。").build())
                .build());

        assertFalse(result.getHasJd());
        assertNull(result.getJdMatch());
        assertEquals(82, result.getQuality().getScore());
        assertNull(result.getQuality().getJdWeight());
    }

    @Test
    @DisplayName("有 JD 评分返回 jdMatch 且质量评分包含 45 权重")
    void evaluateWithJdShouldReturnJdMatchAndFixedWeight() {
        DefaultResumeEvaluationService service = new DefaultResumeEvaluationService(
                new FakeChatModel("""
                        {
                          "originalResume": {"score": 66, "summary": "原始简历可用"},
                          "generatedResume": {"score": 88, "summary": "预览简历贴合 JD", "jdWeight": 45},
                          "quality": {"score": 88, "summary": "预览简历贴合 JD", "jdWeight": 45},
                          "jdMatch": {
                            "score": 91,
                            "summary": "JD 匹配度高",
                            "matchedSkills": ["Java", "Spring Boot"],
                            "missingRequirements": ["Docker"],
                            "bonusItems": ["性能优化"],
                            "suggestions": ["补充 Docker 部署经验"]
                          },
                          "hasJd": true
                        }
                        """),
                new ObjectMapper()
        );

        ResumeEvaluationBundle result = service.evaluateWithJd(ResumeEvaluationRequest.builder()
                .originalResumeText("Java Spring Boot 性能优化")
                .generatedResume(ResumeVO.builder().summary("Java 后端，Spring Boot 项目性能优化。").build())
                .jobDescription("要求 Java、Spring Boot、Docker，有性能优化经验优先")
                .build());

        assertTrue(result.getHasJd());
        assertNotNull(result.getJdMatch());
        assertEquals(91, result.getJdMatch().getScore());
        assertEquals(45, result.getQuality().getJdWeight());
        assertEquals(45, result.getGeneratedResume().getJdWeight());
    }

    @Test
    @DisplayName("批量带 JD 评分返回候选人卡片")
    void evaluateBatchWithJdShouldReturnCandidateCards() {
        DefaultResumeEvaluationService service = new DefaultResumeEvaluationService(
                new FakeChatModel("""
                        {
                          "quality": {"score": 80, "summary": "质量较好", "jdWeight": 45},
                          "jdMatch": {"score": 86, "summary": "匹配较高"},
                          "hasJd": true
                        }
                        """),
                new ObjectMapper()
        );

        List<CandidateResumeEvaluationCard> cards = service.evaluateBatchWithJd(BatchResumeEvaluationRequest.builder()
                .jobDescription("Java 后端 JD")
                .candidates(List.of(
                        BatchResumeEvaluationRequest.CandidateResume.builder()
                                .candidateId("c-1")
                                .candidateName("候选人 A")
                                .originalResumeText("Java 后端")
                                .build()
                ))
                .build());

        assertEquals(1, cards.size());
        assertEquals("c-1", cards.getFirst().getCandidateId());
        assertEquals(86, cards.getFirst().getScore());
        assertNotNull(cards.getFirst().getEvaluation().getJdMatch());
    }

    @Test
    @DisplayName("strict 评分在 LLM 失败时抛出异常供异步任务写入 failed")
    void strictEvaluationShouldThrowWhenLlmFails() {
        DefaultResumeEvaluationService service = new DefaultResumeEvaluationService(
                new FailingChatModel(),
                new ObjectMapper()
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.evaluateWithoutJdStrict(ResumeEvaluationRequest.builder()
                        .originalResumeText("Java 后端")
                        .build()));

        assertTrue(error.getMessage().contains("LLM 评分失败"));
    }

    static class FakeChatModel implements ChatModel {
        private final String response;

        FakeChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().build();
        }

        @Override
        public List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
            return List.of();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OTHER;
        }
    }

    static class FailingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new IllegalStateException("LLM timeout");
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().build();
        }

        @Override
        public List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
            return List.of();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OTHER;
        }
    }
}
