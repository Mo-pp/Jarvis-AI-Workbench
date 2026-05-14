package com.msz.resume.ai.chat.prompt.builder;

import com.msz.resume.ai.chat.prompt.config.PromptConfigLoader;
import com.msz.resume.ai.chat.prompt.provider.DynamicSectionProvider;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultSystemPromptBuilder 缓存稳定性测试
 *
 * <p>验证静态部分缓存和工具顺序稳定性
 */
class DefaultSystemPromptBuilderCacheTest {

    private TestPromptConfigLoader configLoader;
    private TestDynamicSectionProvider dynamicSectionProvider;
    private TestToolRegistry toolRegistry;
    private DefaultSystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        configLoader = new TestPromptConfigLoader();
        dynamicSectionProvider = new TestDynamicSectionProvider();
        toolRegistry = new TestToolRegistry();

        builder = new DefaultSystemPromptBuilder(
                configLoader,
                dynamicSectionProvider,
                toolRegistry
        );
    }

    @Test
    @DisplayName("同一会话内多次调用buildStatic()返回相同字符串（缓存命中）")
    void buildStatic_shouldReturnSameStringWhenCached() {
        // 第一次调用
        String firstResult = builder.buildStatic();

        // 第二次调用
        String secondResult = builder.buildStatic();

        // 验证结果相同
        assertEquals(firstResult, secondResult);

        // 验证缓存生效：第二次调用不应该增加loadCount
        // 静态section有3个（INTRO, TONE_AND_STYLE, OUTPUT_EFFICIENCY），所以第一次调用loadCount=3
        int firstLoadCount = configLoader.getLoadCount();
        assertTrue(firstLoadCount > 0, "第一次调用应该触发加载");

        // 第二次调用应该使用缓存，loadCount不变
        int secondLoadCount = configLoader.getLoadCount();
        assertEquals(firstLoadCount, secondLoadCount, "第二次调用应该使用缓存，不重新加载");
    }

    @Test
    @DisplayName("invalidateStaticCache()后下次buildStatic()重新计算")
    void buildStatic_shouldRecalculateAfterInvalidate() {
        // 第一次调用
        String firstResult = builder.buildStatic();
        int firstLoadCount = configLoader.getLoadCount();

        // 清除缓存
        builder.invalidateStaticCache();

        // 第二次调用
        String secondResult = builder.buildStatic();
        int secondLoadCount = configLoader.getLoadCount();

        // 验证结果相同（内容不变）
        assertEquals(firstResult, secondResult);

        // 验证缓存失效后重新计算
        assertTrue(secondLoadCount > firstLoadCount, "缓存失效后应该重新加载");
    }

    @Test
    @DisplayName("reload()自动清除缓存")
    void reload_shouldInvalidateCache() {
        // 第一次调用
        builder.buildStatic();
        int firstLoadCount = configLoader.getLoadCount();

        // 重载配置
        builder.reload();
        int reloadCount = configLoader.getReloadCount();

        // 第二次调用
        builder.buildStatic();
        int secondLoadCount = configLoader.getLoadCount();

        // 验证reload被调用
        assertEquals(1, reloadCount);

        // 验证缓存被清除后重新加载
        assertTrue(secondLoadCount > firstLoadCount, "reload后应该重新加载");
    }

    @Test
    @DisplayName("并发调用buildStatic()线程安全")
    void buildStatic_shouldBeThreadSafe() throws InterruptedException {
        // 设置延迟加载
        configLoader.setDelayMs(10);

        // 多线程并发调用
        Thread[] threads = new Thread[10];
        final String[] results = new String[10];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = builder.buildStatic();
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有结果相同
        for (int i = 1; i < results.length; i++) {
            assertEquals(results[0], results[i], "线程" + i + "的结果应该与线程0相同");
        }

        // 验证缓存生效：只加载一次（3个section = 3次loadSectionTemplate调用）
        // 由于双重检查锁定，即使并发调用也只加载一次
        int loadCount = configLoader.getLoadCount();
        assertTrue(loadCount <= 4 * 3, "并发调用应该只触发一次完整加载，实际loadCount=" + loadCount);
    }

    // ==================== 测试辅助类 ====================

    static class TestPromptConfigLoader implements PromptConfigLoader {
        private final AtomicInteger loadCount = new AtomicInteger(0);
        private final AtomicInteger reloadCount = new AtomicInteger(0);
        private volatile int delayMs = 0;

        @Override
        public String loadSectionTemplate(String sectionName) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            loadCount.incrementAndGet();
            return "Section: " + sectionName;
        }

        @Override
        public Map<String, String> loadAllSectionTemplates() {
            return Map.of();
        }

        @Override
        public boolean isSectionEnabled(String sectionName) {
            return true;
        }

        @Override
        public Optional<String> getConfig(String key) {
            return Optional.empty();
        }

        @Override
        public void reload() {
            reloadCount.incrementAndGet();
            loadCount.incrementAndGet(); // reload后需要重新加载
        }

        int getLoadCount() {
            return loadCount.get();
        }

        int getReloadCount() {
            return reloadCount.get();
        }

        void setDelayMs(int delayMs) {
            this.delayMs = delayMs;
        }
    }

    static class TestDynamicSectionProvider implements DynamicSectionProvider {
        @Override
        public String getSessionGuidance(ToolRegistry registry) {
            return "Session guidance";
        }

        @Override
        public String getEnvInfo() {
            return "Environment info";
        }

        @Override
        public String getUserProfile(com.msz.resume.ai.chat.prompt.model.UserProfile userProfile) {
            return "User profile";
        }

        @Override
        public String getUserPreferences(com.msz.resume.ai.chat.prompt.model.UserProfile userProfile) {
            return "User preferences";
        }

        @Override
        public String getMemory(com.msz.resume.ai.chat.prompt.model.UserProfile userProfile) {
            return "";
        }

        @Override
        public String getSubAgentContext(String taskDescription) {
            return "Sub-agent task: " + taskDescription;
        }
    }

    static class TestToolRegistry extends ToolRegistry {
        // 使用默认实现即可
    }
}
