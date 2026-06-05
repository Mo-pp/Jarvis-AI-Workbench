package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.MatchAnalysis;
import com.msz.resume.ai.resume.dto.OptimizeResult;
import com.msz.resume.ai.resume.dto.ResumeVO;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布前端工作台产物的统一工具。
 *
 * <p>用于没有专用工具生成结果的产物，例如简历、优化分析、Markdown 预览等。
 * 工具只负责校验并返回严格 JSON artifact；Controller 会统一封装为 ChatArtifact。
 */
@Slf4j
@CoreTool
@Component
public class ArtifactTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "mindmap",
            "resume",
            "optimize_result",
            "resume_evaluation",
            "markdown"
    );

    @Tool("""
            Publish a frontend workbench artifact.
            Use this when you have generated a structured artifact that should open in the workbench.
            For resume generation or resume optimization requests, if you already have enough information, publish the artifact immediately instead of first sending a long prose draft.
            Return one structured artifact object directly instead of wrapping JSON inside a string.
            Supported artifact types include resume, optimize_result, mindmap, and markdown.
            Resume evaluation can be published with type=resume_evaluation after evaluateResume returns a scoring bundle.
            Do not use this for PDF export. PDF export is handled directly by the frontend workbench.
            Do not use this for ordinary prose answers or user-facing plans.
            """)
    public String publishArtifact(
            @P("Artifact type. Supported values: mindmap, resume, optimize_result, resume_evaluation, markdown.") String type,
            @P(value = "Resume artifact payload. Use this when type=resume.", required = false) ResumeVO resume,
            @P(value = "Optimize result payload. Use this when type=optimize_result.", required = false) OptimizeResult optimizeResult,
            @P(value = "Resume evaluation payload. Use this when type=resume_evaluation.", required = false) ResumeEvaluationBundle resumeEvaluation,
            @P(value = "Markdown content. Use this when type=mindmap or type=markdown.", required = false) String markdown
    ) {
        if (type == null || type.isBlank()) {
            return error("type 不能为空");
        }

        if (!SUPPORTED_TYPES.contains(type)) {
            return error("不支持的 artifact type: " + type);
        }

        Object payload = switch (type) {
            case "resume" -> {
                if (resume == null) {
                    yield null;
                }
                yield resume;
            }
            case "optimize_result" -> {
                if (optimizeResult == null) {
                    yield null;
                }
                yield optimizeResult;
            }
            case "resume_evaluation" -> {
                if (resumeEvaluation == null) {
                    yield null;
                }
                yield resumeEvaluation;
            }
            case "mindmap" -> new MindmapArtifact(markdown);
            case "markdown" -> new MarkdownArtifact(markdown);
            default -> null;
        };

        if (payload == null || !isValidArtifact(type, payload)) {
            return error("artifact 内容缺少必要字段: " + type);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", type,
                    "payload", payload
            ));
        } catch (Exception e) {
            log.warn("[ArtifactTool] artifact 序列化失败: {}", e.getMessage());
            return error("artifact 序列化失败: " + e.getMessage());
        }
    }

    private String error(String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "type", "error",
                    "message", message
            ));
        } catch (Exception ignored) {
            return "{\"type\":\"error\",\"message\":\"artifact publish failed\"}";
        }
    }

    private boolean isValidArtifact(String type, Object payload) {
        return switch (type) {
            case "mindmap" -> payload instanceof MindmapArtifact artifact
                    && artifact.getMarkdown() != null
                    && !artifact.getMarkdown().isBlank();
            case "resume" -> payload instanceof ResumeVO;
            case "optimize_result" -> payload instanceof OptimizeResult artifact
                    && (artifact.getMatchScore() != null
                    || hasMatchAnalysisContent(artifact.getMatchAnalysis())
                    || hasContent(artifact.getSuggestions())
                    || hasContent(artifact.getHighlights())
                    || artifact.getEvaluation() != null
                    || artifact.getOptimizedResume() != null);
            case "resume_evaluation" -> payload instanceof ResumeEvaluationBundle artifact
                    && (artifact.getQuality() != null
                    || artifact.getOriginalResume() != null
                    || artifact.getGeneratedResume() != null
                    || artifact.getJdMatch() != null);
            case "markdown" -> payload instanceof MarkdownArtifact artifact
                    && artifact.getMarkdown() != null
                    && !artifact.getMarkdown().isBlank();
            default -> false;
        };
    }

    private boolean hasMatchAnalysisContent(MatchAnalysis analysis) {
        return analysis != null
                && (hasContent(analysis.getMatchedSkills())
                || hasContent(analysis.getMissingSkills())
                || hasContent(analysis.getMatchedBonus())
                || hasText(analysis.getExperienceMatch())
                || hasText(analysis.getEducationMatch()));
    }

    private boolean hasContent(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MindmapArtifact {
        private String markdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MarkdownArtifact {
        private String markdown;
    }

    // Questionnaire artifacts are produced by askUserQuestion/askMultipleQuestions/askQuestionnaire tools.
    // Keep publishArtifact focused on workbench artifacts like resume/optimize_result/markdown/mindmap.
}
