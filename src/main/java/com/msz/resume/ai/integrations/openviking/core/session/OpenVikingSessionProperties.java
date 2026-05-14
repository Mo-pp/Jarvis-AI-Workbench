package com.msz.resume.ai.integrations.openviking.core.session;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenViking Session 集成配置属性。
 *
 * <p>控制 OpenViking Session 功能在 JARVIS 主对话链路中的行为。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jarvis.open-viking.session")
public class OpenVikingSessionProperties {

    /**
     * 主开关：是否启用 OpenViking Session 集成。
     *
     * <p>默认 false，需要显式启用。</p>
     */
    private boolean enabled = false;

    /**
     * 是否追加用户消息到 OpenViking Session。
     *
     * <p>仅在 enabled=true 时生效。</p>
     */
    private boolean appendUser = true;

    /**
     * 是否追加助手消息到 OpenViking Session。
     *
     * <p>仅在 enabled=true 时生效。</p>
     */
    private boolean appendAssistant = true;

    /**
     * 是否在压缩/恢复时加载 OpenViking Session Context。
     *
     * <p>仅在 enabled=true 时生效。</p>
     */
    private boolean contextOnCompact = true;

    /**
     * 是否启用手动 commit 入口。
     */
    private boolean manualCommit = true;

    /**
     * Session Context Token 预算上限。
     *
     * <p>用于控制 loadSessionContext 返回内容的最大 Token 数。</p>
     */
    private int contextTokenBudget = 4000;
}
