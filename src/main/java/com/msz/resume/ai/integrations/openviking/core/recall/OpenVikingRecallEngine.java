package com.msz.resume.ai.integrations.openviking.core.recall;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pre-LLM OpenViking recall coordinator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVikingRecallEngine {

    private static final int MAX_RECENT_USER_MESSAGES = 3;
    private static final int MAX_RECALL_QUERY_CHARS = 600;
    private static final Pattern FOLLOW_UP_QUERY = Pattern.compile(
            "^(有|还有|那|这个|这些|相关|未完成|下一步|继续|展开|详细|具体|为什么|怎么做|咋做|哪些|如何|是否|能不能|可以吗|呢|然后|再说|讲细|说细|补充).{0,80}$"
    );

    private final OpenVikingRecallProperties properties;
    private final OpenVikingRecallTriggerPolicy triggerPolicy;
    private final OpenVikingClient openVikingClient;

    public OpenVikingRecallResult prepare(QueryLoopState state, List<ChatMessage> messages) {
        if (!properties.isEnabled()) {
            return OpenVikingRecallResult.disabled();
        }

        QueryEnvelope query = buildRecallQuery(messages);
        OpenVikingRecallResult result = triggerPolicy.evaluate(query.recallQuery(), properties);
        log.debug("[OpenVikingRecallEngine] Phase1 recall decision: status={}, shouldRecall={}, reason={}, scopes={}",
                result.status(), result.shouldRecall(), result.reason(), result.targetScopes());
        if (!result.shouldRecall() || !properties.isRetrievalEnabled()) {
            return result;
        }
        try {
            Retrieval retrieval = findCandidates(result.query(), result.targetScopes(), state);
            Selection initialSelection = selectCandidates(retrieval.candidates(), state);
            List<OpenVikingRecallResult.RecallCandidate> enriched = initialSelection.selected().stream()
                    .map(this::maybeReadOverview)
                    .toList();
            Selection finalSelection = selectCandidates(enriched, state, initialSelection.suppressed(), initialSelection.budgetTruncated());
            String injectedContext = buildInjectedContext(finalSelection.selected());
            return result.withCandidates(
                    finalSelection.selected(),
                    injectedContext,
                    finalSelection.surfacedUpdates(),
                    finalSelection.suppressed(),
                    retrieval.failedScopes(),
                    finalSelection.budgetTruncated()
            );
        } catch (Exception e) {
            log.warn("[OpenVikingRecallEngine] Resource recall failed: {}", e.getMessage());
            return new OpenVikingRecallResult(
                    true,
                    true,
                    "failed",
                    "retrieval_failed",
                    result.query(),
                    result.triggerReasons(),
                    result.targetScopes(),
                    List.of(),
                    "",
                    Map.of(),
                    List.of(),
                    List.of(),
                    false
            );
        }
    }

    public List<ChatMessage> inject(List<ChatMessage> messages, OpenVikingRecallResult recallResult) {
        if (recallResult == null || recallResult.injectedContext() == null || recallResult.injectedContext().isBlank()) {
            return messages;
        }
        List<ChatMessage> updated = new ArrayList<>();
        updated.add(UserMessage.from(recallResult.injectedContext()));
        if (messages != null) {
            updated.addAll(messages);
        }
        return updated;
    }

    private Retrieval findCandidates(String query, List<String> scopes, QueryLoopState state) {
        List<OpenVikingRecallResult.RecallCandidate> candidates = new ArrayList<>();
        List<OpenVikingRecallResult.FailedScope> failedScopes = new ArrayList<>();
        if (scopes.contains("resource")) {
            findScopedCandidates(query, state, "resource", properties.getResourceRootUri(), properties.getResourceLimit(), candidates, failedScopes);
        }
        if (scopes.contains("memory")) {
            findScopedCandidates(query, state, "memory", memoryRootUri(state), properties.getMemoryLimit(), candidates, failedScopes);
        }
        if (scopes.contains("skill")) {
            findScopedCandidates(query, state, "skill", skillRootUri(state), properties.getSkillLimit(), candidates, failedScopes);
        }
        Map<String, OpenVikingRecallResult.RecallCandidate> deduped = new LinkedHashMap<>();
        for (OpenVikingRecallResult.RecallCandidate candidate : candidates) {
            if (!hasText(candidate.uri())) {
                continue;
            }
            deduped.putIfAbsent(candidate.uri(), candidate);
        }
        List<OpenVikingRecallResult.RecallCandidate> sorted = deduped.values().stream()
                .sorted(Comparator.comparing(OpenVikingRecallResult.RecallCandidate::score,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new Retrieval(sorted, failedScopes);
    }

    private void findScopedCandidates(String query,
                                      QueryLoopState state,
                                      String type,
                                      String targetUri,
                                      int limit,
                                      List<OpenVikingRecallResult.RecallCandidate> candidates,
                                      List<OpenVikingRecallResult.FailedScope> failedScopes) {
        if (!hasText(targetUri)) {
            return;
        }
        OpenVikingFindRequest request = new OpenVikingFindRequest(
                query,
                targetUri,
                limit,
                limit,
                null,
                true
        );
        try {
            OpenVikingFindResponse response = openVikingClient.find(request, identity(state));
            if (response == null || response.result() == null) {
                return;
            }

            List<OpenVikingFindResponse.MatchedContext> raw = switch (type) {
                case "memory" -> response.result().memories();
                case "skill" -> response.result().skills();
                default -> response.result().resources();
            };
            if (raw == null || raw.isEmpty()) {
                return;
            }
            raw.stream()
                    .filter(Objects::nonNull)
                    .map(context -> toCandidate(type, context))
                    .filter(Objects::nonNull)
                    .forEach(candidates::add);
        } catch (Exception e) {
            log.warn("[OpenVikingRecallEngine] {} recall failed: {}", type, e.getMessage());
            failedScopes.add(new OpenVikingRecallResult.FailedScope(type, e.getMessage()));
        }
    }

    private OpenVikingRecallResult.RecallCandidate toCandidate(String type, OpenVikingFindResponse.MatchedContext context) {
        if (context.score() == null || context.score() < properties.getMediumRelevanceThreshold()) {
            return null;
        }
        String relevance = context.score() >= properties.getHighRelevanceThreshold() ? "high" : "medium";
        return new OpenVikingRecallResult.RecallCandidate(
                type,
                context.uri(),
                relevance,
                context.score(),
                context.matchReason(),
                limitText(context.abstractText(), properties.getMaxAbstractChars()),
                "",
                "not_applicable"
        );
    }

    private OpenVikingRecallResult.RecallCandidate maybeReadOverview(OpenVikingRecallResult.RecallCandidate candidate) {
        if (!"resource".equals(candidate.type()) || !properties.isOverviewReadEnabled() || !"high".equals(candidate.relevance()) || !hasText(candidate.uri())) {
            return candidate;
        }
        try {
            OpenVikingReadResponse response = openVikingClient.readOverview(candidate.uri());
            String overview = readResultAsText(response);
            if (!hasText(overview)) {
                return new OpenVikingRecallResult.RecallCandidate(
                        candidate.type(), candidate.uri(), candidate.relevance(), candidate.score(),
                        candidate.matchReason(), candidate.abstractText(), "", "empty"
                );
            }
            return new OpenVikingRecallResult.RecallCandidate(
                    candidate.type(), candidate.uri(), candidate.relevance(), candidate.score(),
                    candidate.matchReason(), candidate.abstractText(),
                    limitText(overview.trim(), properties.getMaxOverviewChars()), "read"
            );
        } catch (Exception e) {
            log.warn("[OpenVikingRecallEngine] Overview read failed for {}: {}", candidate.uri(), e.getMessage());
            return new OpenVikingRecallResult.RecallCandidate(
                    candidate.type(), candidate.uri(), candidate.relevance(), candidate.score(),
                    candidate.matchReason(), candidate.abstractText(), "", "failed"
            );
        }
    }

    private String buildInjectedContext(List<OpenVikingRecallResult.RecallCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<system-reminder>\n");
        sb.append("Jarvis found relevant OpenViking context for this request.\n\n");
        int index = 1;
        for (OpenVikingRecallResult.RecallCandidate candidate : candidates) {
            sb.append(index++).append(". Type: ").append(candidate.type()).append("\n");
            sb.append("   URI: ").append(candidate.uri()).append("\n");
            sb.append("   Relevance: ").append(candidate.relevance()).append("\n");
            if (candidate.score() != null) {
                sb.append("   Score: ").append(String.format("%.3f", candidate.score())).append("\n");
            }
            if (hasText(candidate.matchReason())) {
                sb.append("   Reason: ").append(candidate.matchReason()).append("\n");
            }
            if (hasText(candidate.abstractText())) {
                sb.append("   Abstract:\n   ").append(candidate.abstractText().replace("\n", "\n   ")).append("\n");
            }
            if (hasText(candidate.overviewText())) {
                sb.append("   Overview:\n   ").append(candidate.overviewText().replace("\n", "\n   ")).append("\n");
            }
            sb.append("\n");
        }
        sb.append("When answering, prefer this OpenViking context over generic knowledge if it is relevant. ");
        sb.append("If exact details are needed, call openviking_read(uri=..., level=read) on the URI.\n");
        sb.append("</system-reminder>");
        return limitText(sb.toString(), properties.getMaxInjectedChars());
    }

    private Selection selectCandidates(List<OpenVikingRecallResult.RecallCandidate> candidates, QueryLoopState state) {
        return selectCandidates(candidates, state, List.of(), false);
    }

    private Selection selectCandidates(List<OpenVikingRecallResult.RecallCandidate> candidates,
                                       QueryLoopState state,
                                       List<OpenVikingRecallResult.SuppressedCandidate> existingSuppressed,
                                       boolean alreadyBudgetTruncated) {
        if (candidates == null || candidates.isEmpty()) {
            return new Selection(List.of(), Map.of(),
                    existingSuppressed != null ? List.copyOf(existingSuppressed) : List.of(),
                    alreadyBudgetTruncated);
        }
        Map<String, String> surfaced = state != null ? state.getSurfacedOpenVikingUris() : Map.of();
        List<OpenVikingRecallResult.RecallCandidate> selected = new ArrayList<>();
        List<OpenVikingRecallResult.SuppressedCandidate> suppressed = new ArrayList<>();
        if (existingSuppressed != null) {
            suppressed.addAll(existingSuppressed);
        }
        int usedChars = 0;
        boolean budgetTruncated = alreadyBudgetTruncated;

        for (OpenVikingRecallResult.RecallCandidate candidate : candidates) {
            String level = injectedLevel(candidate);
            String previousLevel = surfaced.get(candidate.uri());
            if (shouldSuppressDuplicate(candidate, previousLevel, level)) {
                suppressed.add(new OpenVikingRecallResult.SuppressedCandidate(
                        candidate.uri(), candidate.type(), "already_surfaced", previousLevel, level
                ));
                continue;
            }
            if (selected.size() >= properties.getMaxInjectedCandidates()) {
                suppressed.add(new OpenVikingRecallResult.SuppressedCandidate(
                        candidate.uri(), candidate.type(), "candidate_limit", previousLevel, level
                ));
                continue;
            }
            int candidateChars = candidateCost(candidate);
            if (usedChars > 0 && usedChars + candidateChars > properties.getMaxInjectedChars()) {
                budgetTruncated = true;
                suppressed.add(new OpenVikingRecallResult.SuppressedCandidate(
                        candidate.uri(), candidate.type(), "budget_limit", previousLevel, level
                ));
                continue;
            }
            usedChars += candidateChars;
            selected.add(candidate);
        }

        Map<String, String> updates = new LinkedHashMap<>();
        for (OpenVikingRecallResult.RecallCandidate candidate : selected) {
            updates.put(candidate.uri(), injectedLevel(candidate));
        }
        return new Selection(selected, updates, suppressed, budgetTruncated);
    }

    private boolean shouldSuppressDuplicate(OpenVikingRecallResult.RecallCandidate candidate, String previousLevel, String candidateLevel) {
        if (!hasText(previousLevel)) {
            return false;
        }
        if ("resource".equals(candidate.type())
                && properties.isOverviewReadEnabled()
                && "high".equals(candidate.relevance())
                && "abstract".equals(previousLevel)
                && "abstract".equals(candidateLevel)) {
            return false;
        }
        return levelRank(previousLevel) >= levelRank(candidateLevel);
    }

    private int levelRank(String level) {
        if ("overview".equals(level)) {
            return 2;
        }
        if ("abstract".equals(level)) {
            return 1;
        }
        return 0;
    }

    private String injectedLevel(OpenVikingRecallResult.RecallCandidate candidate) {
        return hasText(candidate.overviewText()) ? "overview" : "abstract";
    }

    private int candidateCost(OpenVikingRecallResult.RecallCandidate candidate) {
        int cost = 240;
        if (candidate.uri() != null) {
            cost += candidate.uri().length();
        }
        if (candidate.matchReason() != null) {
            cost += candidate.matchReason().length();
        }
        if (candidate.abstractText() != null) {
            cost += candidate.abstractText().length();
        }
        if (candidate.overviewText() != null) {
            cost += candidate.overviewText().length();
        }
        return cost;
    }

    private String memoryRootUri(QueryLoopState state) {
        OpenVikingIdentity identity = identity(state);
        String userId = identity != null && hasText(identity.user()) ? identity.user() : null;
        if (!hasText(userId)) {
            return "";
        }
        return properties.getMemoryRootUriTemplate().replace("{userId}", userId);
    }

    private String skillRootUri(QueryLoopState state) {
        OpenVikingIdentity identity = identity(state);
        String agentId = identity != null && hasText(identity.agent()) ? identity.agent() : null;
        if (!hasText(agentId)) {
            return "";
        }
        return properties.getSkillRootUriTemplate().replace("{agentId}", agentId);
    }

    private String latestUserText(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof UserMessage userMessage) {
                try {
                    return userMessage.singleText();
                } catch (Exception ignored) {
                    return "";
                }
            }
        }
        return "";
    }

    private QueryEnvelope buildRecallQuery(List<ChatMessage> messages) {
        List<String> userTexts = recentUserTexts(messages);
        if (userTexts.isEmpty()) {
            return new QueryEnvelope("", "");
        }
        String latest = userTexts.get(userTexts.size() - 1);
        if (userTexts.size() == 1 || !isFollowUpQuery(latest)) {
            return new QueryEnvelope(latest, latest);
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, userTexts.size() - MAX_RECENT_USER_MESSAGES);
        for (int i = start; i < userTexts.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(i == userTexts.size() - 1 ? "当前追问: " : "相关上文: ");
            sb.append(userTexts.get(i));
        }
        return new QueryEnvelope(latest, limitText(sb.toString(), MAX_RECALL_QUERY_CHARS));
    }

    private List<String> recentUserTexts(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                try {
                    String text = userMessage.singleText();
                    if (hasText(text)) {
                        texts.add(text.trim());
                    }
                } catch (Exception ignored) {
                    // Non-text user messages are not useful for OV text recall.
                }
            }
        }
        return Collections.unmodifiableList(texts);
    }

    private boolean isFollowUpQuery(String text) {
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        return normalized.length() <= 80 && FOLLOW_UP_QUERY.matcher(normalized).matches();
    }

    private String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String readResultAsText(OpenVikingReadResponse response) {
        if (response == null || response.result() == null) {
            return "";
        }
        return response.result().toString();
    }

    private OpenVikingIdentity identity(QueryLoopState state) {
        return state != null ? state.getOpenVikingIdentity() : null;
    }

    public Map<String, String> mergeSurfacedUris(QueryLoopState state, OpenVikingRecallResult recallResult) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (state != null) {
            merged.putAll(state.getSurfacedOpenVikingUris());
        }
        if (recallResult != null && recallResult.surfacedUriLevels() != null) {
            for (Map.Entry<String, String> entry : recallResult.surfacedUriLevels().entrySet()) {
                String existing = merged.get(entry.getKey());
                if (levelRank(entry.getValue()) >= levelRank(existing)) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return merged;
    }

    private record Selection(
            List<OpenVikingRecallResult.RecallCandidate> selected,
            Map<String, String> surfacedUpdates,
            List<OpenVikingRecallResult.SuppressedCandidate> suppressed,
            boolean budgetTruncated
    ) {
    }

    private record Retrieval(
            List<OpenVikingRecallResult.RecallCandidate> candidates,
            List<OpenVikingRecallResult.FailedScope> failedScopes
    ) {
    }

    private record QueryEnvelope(
            String latestUserText,
            String recallQuery
    ) {
    }
}
