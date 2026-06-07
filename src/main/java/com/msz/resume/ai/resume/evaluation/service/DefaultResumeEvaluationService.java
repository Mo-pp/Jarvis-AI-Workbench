package com.msz.resume.ai.resume.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.ResumeVO;
import com.msz.resume.ai.resume.evaluation.dto.BatchResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.CandidateResumeEvaluationCard;
import com.msz.resume.ai.resume.evaluation.dto.JdMatchEvaluation;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.ResumeQualityEvaluation;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DefaultResumeEvaluationService implements ResumeEvaluationService {

    private static final int JD_WEIGHT = 45;
    private static final int MAX_TEXT_CHARS = 18_000;
    private static final int MAX_GENERATED_JSON_CHARS = 18_000;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public DefaultResumeEvaluationService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeEvaluationBundle evaluateWithoutJd(ResumeEvaluationRequest request) {
        ResumeEvaluationRequest normalized = normalizeRequest(request);
        ResumeEvaluationBundle bundle = evaluate(normalized, false, false);
        bundle.setJdMatch(null);
        bundle.setHasJd(false);
        return bundle;
    }

    @Override
    public ResumeEvaluationBundle evaluateWithoutJdStrict(ResumeEvaluationRequest request) {
        ResumeEvaluationRequest normalized = normalizeRequest(request);
        ResumeEvaluationBundle bundle = evaluate(normalized, false, true);
        bundle.setJdMatch(null);
        bundle.setHasJd(false);
        return bundle;
    }

    @Override
    public ResumeEvaluationBundle evaluateWithJd(ResumeEvaluationRequest request) {
        ResumeEvaluationRequest normalized = normalizeRequest(request);
        if (!hasText(normalized.getJobDescription())) {
            throw new IllegalArgumentException("JD 不能为空");
        }
        ResumeEvaluationBundle bundle = evaluate(normalized, true, false);
        applyJdMetadata(bundle);
        return bundle;
    }

    @Override
    public ResumeEvaluationBundle evaluateWithJdStrict(ResumeEvaluationRequest request) {
        ResumeEvaluationRequest normalized = normalizeRequest(request);
        if (!hasText(normalized.getJobDescription())) {
            throw new IllegalArgumentException("JD 不能为空");
        }
        ResumeEvaluationBundle bundle = evaluate(normalized, true, true);
        applyJdMetadata(bundle);
        return bundle;
    }

    private void applyJdMetadata(ResumeEvaluationBundle bundle) {
        bundle.setHasJd(true);
        if (bundle.getGeneratedResume() != null) {
            bundle.getGeneratedResume().setJdWeight(JD_WEIGHT);
        }
        if (bundle.getOriginalResume() != null) {
            bundle.getOriginalResume().setJdWeight(JD_WEIGHT);
        }
        if (bundle.getQuality() != null) {
            bundle.getQuality().setJdWeight(JD_WEIGHT);
        }
    }

    @Override
    public List<CandidateResumeEvaluationCard> evaluateBatchWithJd(BatchResumeEvaluationRequest request) {
        if (request == null || !hasText(request.getJobDescription())) {
            throw new IllegalArgumentException("JD 不能为空");
        }
        List<BatchResumeEvaluationRequest.CandidateResume> candidates =
                request.getCandidates() != null ? request.getCandidates() : List.of();
        List<CandidateResumeEvaluationCard> cards = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            BatchResumeEvaluationRequest.CandidateResume candidate = candidates.get(i);
            ResumeEvaluationRequest itemRequest = ResumeEvaluationRequest.builder()
                    .originalResumeText(candidate.getOriginalResumeText())
                    .generatedResume(candidate.getGeneratedResume())
                    .jobDescription(request.getJobDescription())
                    .targetPosition(request.getTargetPosition())
                    .build();
            ResumeEvaluationBundle evaluation = evaluateWithJd(itemRequest);
            JdMatchEvaluation jdMatch = evaluation.getJdMatch();
            ResumeQualityEvaluation quality = evaluation.getQuality();
            Integer score = jdMatch != null && jdMatch.getScore() != null
                    ? jdMatch.getScore()
                    : quality != null ? quality.getScore() : null;
            cards.add(CandidateResumeEvaluationCard.builder()
                    .candidateId(hasText(candidate.getCandidateId()) ? candidate.getCandidateId() : "candidate-" + (i + 1))
                    .candidateName(resolveCandidateName(candidate))
                    .score(score)
                    .summary(jdMatch != null && hasText(jdMatch.getSummary())
                            ? jdMatch.getSummary()
                            : quality != null ? quality.getSummary() : null)
                    .evaluation(evaluation)
                    .build());
        }
        return cards;
    }

    private ResumeEvaluationBundle evaluate(ResumeEvaluationRequest request, boolean withJd, boolean failOnLlmError) {
        try {
            String prompt = buildEvaluationPrompt(request, withJd);
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt()),
                            UserMessage.from(prompt)
                    ))
                    .build());
            String text = response.aiMessage() != null ? response.aiMessage().text() : null;
            ResumeEvaluationBundle parsed = parseBundle(text, withJd);
            return normalizeBundle(parsed, request, withJd);
        } catch (Exception e) {
            if (failOnLlmError) {
                throw new IllegalStateException("LLM 评分失败: " + e.getMessage(), e);
            }
            log.warn("[ResumeEvaluationService] LLM 评分失败，使用规则评分兜底: {}", e.getMessage());
            return fallbackBundle(request, withJd);
        }
    }

    private String systemPrompt() {
        return """
                你是 Jarvis 的简历评分子 agent。只负责简历评价和 JD 匹配评分，不负责改写简历。
                必须返回一个严格 JSON object，不要 Markdown，不要解释文字，不要代码块。
                评分范围 0-100，分数必须是整数。
                如果没有 JD，禁止返回 jdMatch 字段。
                如果有 JD，简历质量评分里 JD 相关性权重必须固定为 45。
                不要编造候选人没有体现的经历、技能、学历、链接或指标。
                """;
    }

    private String buildEvaluationPrompt(ResumeEvaluationRequest request, boolean withJd) throws Exception {
        String generatedResumeJson = request.getGeneratedResume() != null
                ? truncate(objectMapper.writeValueAsString(request.getGeneratedResume()), MAX_GENERATED_JSON_CHARS)
                : "";
        return """
                请基于以下输入输出 JSON。

                输出 JSON schema：
                {
                  "originalResume": {
                    "score": 0,
                    "summary": "",
                    "jdWeight": null,
                    "dimensionScores": {
                      "brevityBalance": 0,
                      "impactOrientation": 0,
                      "educationBonus": 0,
                      "linkBonus": 0,
                      "technicalStrength": 0,
                      "readability": 0,
                      "jdRelevance": 0
                    },
                    "strengths": [],
                    "issues": [],
                    "suggestions": []
                  },
                  "generatedResume": { "同 originalResume": "结构相同" },
                  "quality": { "同 generatedResume": "没有 generatedResume 时等于 originalResume" },
                  "jdMatch": {
                    "score": 0,
                    "summary": "",
                    "matchedSkills": [],
                    "missingRequirements": [],
                    "bonusItems": [],
                    "suggestions": []
                  },
                  "hasJd": false,
                  "targetPosition": ""
                }

                评分规则：
                - 内容繁简适中：太少、太空、太长、太杂都扣分。
                - 结果/影响导向：强调业务结果、性能收益、成本节省、稳定性、用户量、效率提升；只写实现功能模块要扣分。
                - 学历加分：学历、学校、专业、成绩、排名等有可信信息可加分。
                - GitHub/演示链接加分：有 GitHub、线上 demo、作品链接、项目地址可加分。
                - 技术牛逼度：算法、高并发、分布式、性能优化、AI/RAG/Agent、复杂工程问题等真实体现可加分。
                - 可读性：结构清楚、重点明确、面试官快速扫描友好。
                - 有 JD 时 quality 必须包含 jdWeight=45，且 dimensionScores.jdRelevance 反映 JD 相关性。
                - 没有 JD 时不要输出 jdMatch，jdWeight 填 null 或省略。

                当前是否有 JD：%s
                目标岗位：%s

                JD：
                %s

                原始简历文本：
                %s

                Jarvis 生成/预览简历 JSON：
                %s
                """.formatted(
                withJd,
                blankTo(request.getTargetPosition(), "未指定"),
                withJd ? truncate(request.getJobDescription(), MAX_TEXT_CHARS) : "未提供",
                truncate(blankTo(request.getOriginalResumeText(), ""), MAX_TEXT_CHARS),
                generatedResumeJson
        );
    }

    private ResumeEvaluationBundle parseBundle(String rawText, boolean withJd) throws Exception {
        if (!hasText(rawText)) {
            throw new IllegalArgumentException("评分子 agent 返回为空");
        }
        JsonNode node = extractJsonObject(rawText);
        if (node == null) {
            throw new IllegalArgumentException("评分子 agent 未返回 JSON object");
        }
        ResumeEvaluationBundle bundle = objectMapper.convertValue(node, ResumeEvaluationBundle.class);
        if (!withJd) {
            bundle.setJdMatch(null);
        }
        return bundle;
    }

    private ResumeEvaluationBundle normalizeBundle(ResumeEvaluationBundle bundle,
                                                   ResumeEvaluationRequest request,
                                                   boolean withJd) {
        if (bundle == null) {
            return fallbackBundle(request, withJd);
        }
        ResumeQualityEvaluation original = normalizeQuality(bundle.getOriginalResume(), "原始简历内容已完成质量评分", withJd);
        ResumeQualityEvaluation generated = normalizeQuality(bundle.getGeneratedResume(), "Jarvis 预览简历已完成质量评分", withJd);
        if (request.getGeneratedResume() == null && generated.getScore() == 0 && original.getScore() > 0) {
            generated = null;
        }
        ResumeQualityEvaluation quality = normalizeQuality(bundle.getQuality(), null, withJd);
        if (quality.getScore() == 0) {
            quality = generated != null ? generated : original;
        }
        JdMatchEvaluation jdMatch = withJd ? normalizeJdMatch(bundle.getJdMatch(), request) : null;
        return ResumeEvaluationBundle.builder()
                .originalResume(original)
                .generatedResume(generated)
                .quality(quality)
                .jdMatch(jdMatch)
                .hasJd(withJd)
                .targetPosition(blankTo(bundle.getTargetPosition(), request.getTargetPosition()))
                .build();
    }

    private ResumeQualityEvaluation normalizeQuality(ResumeQualityEvaluation quality,
                                                     String fallbackSummary,
                                                     boolean withJd) {
        if (quality == null) {
            quality = new ResumeQualityEvaluation();
        }
        quality.setScore(clampScore(quality.getScore()));
        quality.setSummary(blankTo(quality.getSummary(), fallbackSummary != null ? fallbackSummary : "简历质量评分已生成"));
        quality.setJdWeight(withJd ? JD_WEIGHT : null);
        quality.setDimensionScores(normalizeDimensionScores(quality.getDimensionScores(), withJd));
        quality.setStrengths(cleanList(quality.getStrengths()));
        quality.setIssues(cleanList(quality.getIssues()));
        quality.setSuggestions(cleanList(quality.getSuggestions()));
        return quality;
    }

    private JdMatchEvaluation normalizeJdMatch(JdMatchEvaluation jdMatch, ResumeEvaluationRequest request) {
        if (jdMatch == null) {
            jdMatch = fallbackJdMatch(request);
        }
        jdMatch.setScore(clampScore(jdMatch.getScore()));
        jdMatch.setSummary(blankTo(jdMatch.getSummary(), "已根据 JD 完成匹配度评分"));
        jdMatch.setMatchedSkills(cleanList(jdMatch.getMatchedSkills()));
        jdMatch.setMissingRequirements(cleanList(jdMatch.getMissingRequirements()));
        jdMatch.setBonusItems(cleanList(jdMatch.getBonusItems()));
        jdMatch.setSuggestions(cleanList(jdMatch.getSuggestions()));
        return jdMatch;
    }

    private Map<String, Integer> normalizeDimensionScores(Map<String, Integer> raw, boolean withJd) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("brevityBalance", 0);
        scores.put("impactOrientation", 0);
        scores.put("educationBonus", 0);
        scores.put("linkBonus", 0);
        scores.put("technicalStrength", 0);
        scores.put("readability", 0);
        if (withJd) {
            scores.put("jdRelevance", 0);
        }
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key != null && scores.containsKey(key)) {
                    scores.put(key, clampScore(value));
                }
            });
        }
        return scores;
    }

    private ResumeEvaluationBundle fallbackBundle(ResumeEvaluationRequest request, boolean withJd) {
        ResumeQualityEvaluation original = fallbackQuality(request.getOriginalResumeText(), null, withJd);
        ResumeQualityEvaluation generated = request.getGeneratedResume() != null
                ? fallbackQuality(resumeToText(request.getGeneratedResume()), request.getGeneratedResume(), withJd)
                : null;
        ResumeQualityEvaluation quality = generated != null ? generated : original;
        JdMatchEvaluation jdMatch = withJd ? fallbackJdMatch(request) : null;
        return ResumeEvaluationBundle.builder()
                .originalResume(original)
                .generatedResume(generated)
                .quality(quality)
                .jdMatch(jdMatch)
                .hasJd(withJd)
                .targetPosition(request.getTargetPosition())
                .build();
    }

    private ResumeQualityEvaluation fallbackQuality(String text, ResumeVO resume, boolean withJd) {
        String source = blankTo(text, resumeToText(resume));
        int lengthScore = lengthScore(source);
        int impact = containsAny(source, "提升", "降低", "%", "QPS", "用户", "成本", "性能", "稳定", "效率", "延迟", "命中率") ? 76 : 48;
        int education = containsAny(source, "本科", "硕士", "博士", "大学", "学院", "GPA", "排名") ? 72 : 42;
        int link = containsAny(source.toLowerCase(Locale.ROOT), "github", "http://", "https://", "demo") ? 85 : 35;
        int technical = containsAny(source, "算法", "高并发", "分布式", "RAG", "Agent", "缓存", "性能优化", "异步", "架构") ? 76 : 52;
        int readability = source.length() > 80 ? 68 : 42;
        int jdRelevance = withJd ? 60 : 0;
        int score = withJd
                ? Math.round((lengthScore + impact + education + link + technical + readability) * 0.55f / 6f + jdRelevance * 0.45f)
                : Math.round((lengthScore + impact + education + link + technical + readability) / 6f);

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("brevityBalance", lengthScore);
        dimensions.put("impactOrientation", impact);
        dimensions.put("educationBonus", education);
        dimensions.put("linkBonus", link);
        dimensions.put("technicalStrength", technical);
        dimensions.put("readability", readability);
        if (withJd) {
            dimensions.put("jdRelevance", jdRelevance);
        }
        return ResumeQualityEvaluation.builder()
                .score(clampScore(score))
                .summary("已基于简历文本完成规则评分；模型评分不可用时使用该结果兜底。")
                .jdWeight(withJd ? JD_WEIGHT : null)
                .dimensionScores(dimensions)
                .strengths(List.of("已识别核心简历内容"))
                .issues(List.of())
                .suggestions(List.of("建议补充可量化结果、业务影响和关键技术难点"))
                .build();
    }

    private JdMatchEvaluation fallbackJdMatch(ResumeEvaluationRequest request) {
        Set<String> jdTerms = extractTerms(request.getJobDescription());
        String resumeText = (blankTo(request.getOriginalResumeText(), "") + "\n" + resumeToText(request.getGeneratedResume()))
                .toLowerCase(Locale.ROOT);
        List<String> matched = jdTerms.stream()
                .filter(term -> resumeText.contains(term.toLowerCase(Locale.ROOT)))
                .limit(12)
                .toList();
        List<String> missing = jdTerms.stream()
                .filter(term -> !resumeText.contains(term.toLowerCase(Locale.ROOT)))
                .limit(8)
                .toList();
        int score = jdTerms.isEmpty() ? 50 : Math.round(matched.size() * 100f / jdTerms.size());
        return JdMatchEvaluation.builder()
                .score(clampScore(score))
                .summary("已基于 JD 关键词覆盖完成规则匹配评分；模型评分不可用时使用该结果兜底。")
                .matchedSkills(matched)
                .missingRequirements(missing)
                .bonusItems(List.of())
                .suggestions(missing.isEmpty()
                        ? List.of("继续强化与 JD 直接相关的项目成果")
                        : List.of("补充或突出缺失要求对应的真实项目经历：" + String.join("、", missing)))
                .build();
    }

    private Set<String> extractTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        if (!hasText(text)) {
            return terms;
        }
        String normalized = text.replaceAll("[,，。；;：:\\s/|]+", " ");
        for (String part : normalized.split(" ")) {
            String term = part.trim();
            if (term.length() >= 2 && term.length() <= 24) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String resumeToText(ResumeVO resume) {
        if (resume == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (resume.getBasicInfo() != null) {
            parts.add(resume.getBasicInfo().getName());
            parts.add(resume.getBasicInfo().getPosition());
            parts.add(resume.getBasicInfo().getEducationLevel());
            parts.add(resume.getBasicInfo().getExperience());
        }
        if (resume.getJobIntention() != null) {
            parts.add(resume.getJobIntention().getPosition());
        }
        parts.add(resume.getSummary());
        if (resume.getEducationList() != null) {
            resume.getEducationList().forEach(item -> {
                parts.add(item.getSchool());
                parts.add(item.getMajor());
                parts.add(item.getDegree());
                parts.add(item.getDescription());
            });
        }
        if (resume.getWorkList() != null) {
            resume.getWorkList().forEach(item -> {
                parts.add(item.getCompany());
                parts.add(item.getPosition());
                parts.add(item.getDescription());
            });
        }
        if (resume.getProjectList() != null) {
            resume.getProjectList().forEach(item -> {
                parts.add(item.getName());
                parts.add(item.getRole());
                parts.add(item.getTechStack());
                parts.add(item.getLinks());
                parts.add(item.getDescription());
            });
        }
        if (resume.getSkillList() != null) {
            resume.getSkillList().forEach(item -> {
                parts.add(item.getName());
                parts.add(item.getLevel());
                parts.add(item.getDescription());
            });
        }
        return String.join("\n", parts.stream().filter(this::hasText).toList());
    }

    private JsonNode extractJsonObject(String text) throws Exception {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("(?s)^```\\w*\\s*", "").replaceFirst("(?s)```\\s*$", "").trim();
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return objectMapper.readTree(trimmed);
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return objectMapper.readTree(trimmed.substring(start, end + 1));
        }
        return null;
    }

    private ResumeEvaluationRequest normalizeRequest(ResumeEvaluationRequest request) {
        return request != null ? request : new ResumeEvaluationRequest();
    }

    private String resolveCandidateName(BatchResumeEvaluationRequest.CandidateResume candidate) {
        if (hasText(candidate.getCandidateName())) {
            return candidate.getCandidateName();
        }
        ResumeVO.BasicInfo basicInfo = candidate.getGeneratedResume() != null ? candidate.getGeneratedResume().getBasicInfo() : null;
        if (basicInfo != null && hasText(basicInfo.getName())) {
            return basicInfo.getName();
        }
        return "候选人";
    }

    private int lengthScore(String text) {
        int length = text != null ? text.length() : 0;
        if (length < 120) return 35;
        if (length < 500) return 62;
        if (length <= 3500) return 82;
        if (length <= 6000) return 66;
        return 45;
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private int clampScore(Integer score) {
        if (score == null) return 0;
        return Math.min(Math.max(score, 0), 100);
    }

    private List<String> cleanList(List<String> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(this::hasText)
                .map(String::trim)
                .limit(12)
                .toList();
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n[内容过长，已截断]";
    }

    private String blankTo(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
