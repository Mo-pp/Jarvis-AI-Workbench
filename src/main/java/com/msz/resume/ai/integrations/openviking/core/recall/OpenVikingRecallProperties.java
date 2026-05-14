package com.msz.resume.ai.integrations.openviking.core.recall;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenViking automatic recall configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jarvis.open-viking.recall")
public class OpenVikingRecallProperties {

    /**
     * Master switch for pre-LLM OpenViking recall.
     */
    private boolean enabled = true;

    /**
     * Phase 1 runs only trigger/skip decisions and trace events.
     * Later phases will turn this on when retrieval is implemented.
     */
    private boolean retrievalEnabled = false;

    private boolean memoryScopeEnabled = true;

    private boolean resourceScopeEnabled = true;

    private boolean skillScopeEnabled = true;

    private String resourceRootUri = "viking://resources/";

    private String memoryRootUriTemplate = "viking://user/{userId}/memories/";

    private String skillRootUriTemplate = "viking://agent/{agentId}/skills/";

    private int resourceLimit = 3;

    private int memoryLimit = 3;

    private int skillLimit = 3;

    private double highRelevanceThreshold = 0.75;

    private double mediumRelevanceThreshold = 0.55;

    private int maxInjectedCandidates = 3;

    private int maxInjectedChars = 4000;

    private int maxAbstractChars = 900;

    private boolean overviewReadEnabled = true;

    private int maxOverviewChars = 2200;
}
