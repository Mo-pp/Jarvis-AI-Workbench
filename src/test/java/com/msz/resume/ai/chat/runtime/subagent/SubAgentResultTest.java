package com.msz.resume.ai.chat.runtime.subagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentResultTest {

    @Test
    @DisplayName("强制收束结果应区别于纯轮次超限")
    void wrappedUpShouldNotBeTreatedAsMaxTurnsExceeded() {
        SubAgentResult result = SubAgentResult.wrappedUp("final findings", 55, 55, 100, 10);

        assertTrue(result.isWrappedUp());
        assertFalse(result.isMaxTurnsExceeded());
        assertFalse(result.isError());
    }
}
