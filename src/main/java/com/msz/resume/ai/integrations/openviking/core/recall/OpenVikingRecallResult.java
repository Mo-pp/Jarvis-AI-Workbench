package com.msz.resume.ai.integrations.openviking.core.recall;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 1 recall outcome: no retrieval yet, only trigger/skip metadata.
 */
public record OpenVikingRecallResult(
        boolean enabled,
        boolean shouldRecall,
        String status,
        String reason,
        String query,
        List<String> triggerReasons,
        List<String> targetScopes,
        List<RecallCandidate> candidates,
        String injectedContext,
        Map<String, String> surfacedUriLevels,
        List<SuppressedCandidate> suppressedCandidates,
        List<FailedScope> failedScopes,
        boolean budgetTruncated
) {

    public static OpenVikingRecallResult disabled() {
        return new OpenVikingRecallResult(false, false, "skipped", "disabled", "", List.of(), List.of(), List.of(), "",
                Map.of(), List.of(), List.of(), false);
    }

    public static OpenVikingRecallResult skipped(String reason, String query) {
        return new OpenVikingRecallResult(true, false, "skipped", reason, query != null ? query : "", List.of(), List.of(), List.of(), "",
                Map.of(), List.of(), List.of(), false);
    }

    public static OpenVikingRecallResult triggered(String query, List<String> triggerReasons, List<String> targetScopes) {
        return new OpenVikingRecallResult(true, true, "triggered", "trigger_policy_matched",
                query != null ? query : "", List.copyOf(triggerReasons), List.copyOf(targetScopes), List.of(), "",
                Map.of(), List.of(), List.of(), false);
    }

    public OpenVikingRecallResult withCandidates(List<RecallCandidate> candidates,
                                                 String injectedContext,
                                                 Map<String, String> surfacedUriLevels,
                                                 List<SuppressedCandidate> suppressedCandidates,
                                                 List<FailedScope> failedScopes,
                                                 boolean budgetTruncated) {
        boolean hasCandidates = candidates != null && !candidates.isEmpty();
        boolean hasFailedScopes = failedScopes != null && !failedScopes.isEmpty();
        return new OpenVikingRecallResult(
                enabled,
                shouldRecall,
                hasCandidates ? "injected" : (hasFailedScopes ? "failed" : "no_candidates"),
                hasCandidates ? "candidates_selected" : (hasFailedScopes ? "retrieval_failed" : "no_relevant_candidates"),
                query,
                triggerReasons,
                targetScopes,
                candidates != null ? List.copyOf(candidates) : List.of(),
                injectedContext != null ? injectedContext : "",
                surfacedUriLevels != null ? Map.copyOf(surfacedUriLevels) : Map.of(),
                suppressedCandidates != null ? List.copyOf(suppressedCandidates) : List.of(),
                failedScopes != null ? List.copyOf(failedScopes) : List.of(),
                budgetTruncated
        );
    }

    public Map<String, Object> toTraceMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("enabled", enabled);
        meta.put("shouldRecall", shouldRecall);
        meta.put("status", status);
        meta.put("reason", reason);
        meta.put("query", truncate(query, 300));
        meta.put("triggerReasons", triggerReasons);
        meta.put("targetScopes", targetScopes);
        meta.put("candidateCount", candidates.size());
        meta.put("candidates", candidates.stream().map(RecallCandidate::toTraceMeta).toList());
        meta.put("injected", injectedContext != null && !injectedContext.isBlank());
        meta.put("surfacedUriLevels", surfacedUriLevels);
        meta.put("suppressedCount", suppressedCandidates.size());
        meta.put("suppressedCandidates", suppressedCandidates.stream().map(SuppressedCandidate::toTraceMeta).toList());
        meta.put("failedScopeCount", failedScopes.size());
        meta.put("failedScopes", failedScopes.stream().map(FailedScope::toTraceMeta).toList());
        meta.put("budgetTruncated", budgetTruncated);
        meta.put("phase", candidates.isEmpty() ? "trigger_only" : "recall_context");
        return meta;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "...";
    }

    public record RecallCandidate(
            String type,
            String uri,
            String relevance,
            Double score,
            String matchReason,
            String abstractText,
            String overviewText,
            String overviewStatus
    ) {
        Map<String, Object> toTraceMeta() {
            return Map.of(
                    "type", safe(type),
                    "uri", safe(uri),
                    "relevance", safe(relevance),
                    "score", score != null ? score : 0.0,
                    "matchReason", truncate(matchReason, 240),
                    "abstract", truncate(abstractText, 240),
                    "overviewStatus", safe(overviewStatus),
                    "overview", truncate(overviewText, 240)
            );
        }

        private static String safe(String value) {
            return value != null ? value : "";
        }
    }

    public record SuppressedCandidate(
            String uri,
            String type,
            String reason,
            String previousLevel,
            String candidateLevel
    ) {
        Map<String, Object> toTraceMeta() {
            return Map.of(
                    "uri", safe(uri),
                    "type", safe(type),
                    "reason", safe(reason),
                    "previousLevel", safe(previousLevel),
                    "candidateLevel", safe(candidateLevel)
            );
        }

        private static String safe(String value) {
            return value != null ? value : "";
        }
    }

    public record FailedScope(
            String scope,
            String reason
    ) {
        Map<String, Object> toTraceMeta() {
            return Map.of(
                    "scope", safe(scope),
                    "reason", truncate(reason, 240)
            );
        }

        private static String safe(String value) {
            return value != null ? value : "";
        }
    }
}
