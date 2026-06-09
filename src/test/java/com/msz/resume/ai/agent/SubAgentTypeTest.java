package com.msz.resume.ai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentTypeTest {

    @Test
    @DisplayName("子Agent类型解析应兼容大小写、连字符和下划线")
    void fromNameShouldParseCaseInsensitiveAliases() {
        assertEquals(SubAgentType.Explore, SubAgentType.fromName("Explore"));
        assertEquals(SubAgentType.Explore, SubAgentType.fromName("explore"));
        assertEquals(SubAgentType.Plan, SubAgentType.fromName("PLAN"));
        assertEquals(SubAgentType.ResumeBusinessExplore, SubAgentType.fromName("ResumeBusinessExplore"));
        assertEquals(SubAgentType.ResumeBusinessExplore, SubAgentType.fromName("resume_business_explore"));
        assertEquals(SubAgentType.ResumeBusinessExplore, SubAgentType.fromName("resume-business-explore"));
    }

    @Test
    @DisplayName("未知子Agent类型应回退到General")
    void fromNameShouldFallbackToGeneralForUnknownValues() {
        assertEquals(SubAgentType.General, SubAgentType.fromName(null));
        assertEquals(SubAgentType.General, SubAgentType.fromName(" "));
        assertEquals(SubAgentType.General, SubAgentType.fromName("Unknown"));
    }

    @Test
    @DisplayName("ResumeBusinessExplore 应是精确白名单只读类型")
    void resumeBusinessExploreShouldBeReadOnlyExactWhitelist() {
        assertTrue(SubAgentType.ResumeBusinessExplore.isReadOnly());
        assertTrue(SubAgentType.ResumeBusinessExplore.usesExactToolWhitelist());
    }
}
