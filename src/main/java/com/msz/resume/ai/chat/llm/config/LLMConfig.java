package com.msz.resume.ai.chat.llm.config;

import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;
import java.util.Set;
/**
 * LLM 模型配置类
 *
 * <p>支持多个模型提供商：
 * <ul>
 *   <li>智谱 AI (BigModel) - OpenAI 兼容模式</li>
 *   <li>通义千问 (DashScope) - 通过 community starter 自动配置</li>
 *   <li>GPT - OpenAI Chat / Responses API 兼容模式</li>
 * </ul>
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * jarvis:
 *   llm:
 *     provider: zhipu  # 或 dashscope / gpt
 *     zhipu:
 *       api-key: your-api-key
 *       model: glm-4.7
 *     dashscope:
 *       model: qwen-max
 *     gpt:
 *       api-key: ${OPENAI_API_KEY:}
 *       model: gpt-5.4
 * </pre>
 *
 * <p>注意：当 provider=dashscope 时，使用 langchain4j-community-dashscope-spring-boot-starter
 * 自动配置的 ChatModel，无需手动创建。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "jarvis.llm")
public class LLMConfig {

    /** 当前使用的模型提供商：zhipu、dashscope、gpt 或 qianfan-coding-plan */
    private String provider = "dashscope";

    /** 智谱配置 */
    private ZhipuConfig zhipu = new ZhipuConfig();

    /** 通义千问配置 */
    private DashScopeConfig dashscope = new DashScopeConfig();

    /** GPT 配置 */
    private GptConfig gpt = new GptConfig();

    /** 千帆 Coding Plan 配置（OpenAI Chat Completions 兼容） */
    private QianfanCodingPlanConfig qianfanCodingPlan = new QianfanCodingPlanConfig();

    @Data
    public static class ZhipuConfig {
        /** 智谱 API Key */
        private String apiKey;

        /** 模型名称：glm-4-flash, glm-4, glm-4-plus, glm-4.7 等 */
        private String model = "glm-4.7";

        /** API 基础地址 */
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4/";

        /** 请求超时时间（秒） */
        private int timeout = 60;

        /** 温度参数 */
        private Double temperature = 0.7;

        /** 最大输出 Token */
        private Integer maxTokens = 4096;
    }

    @Data
    public static class DashScopeConfig {
        /** 模型名称 */
        private String model = "qwen-max";
    }

    @Data
    public static class GptConfig {
        /** OpenAI API Key，从 OPENAI_API_KEY 环境变量读取 */
        private String apiKey;

        /** 模型名称 */
        private String model = "gpt-5.4";

        /** API 基础地址 */
        private String baseUrl = "https://api.openai.com/v1";

        /** 线协议：chat 或 responses */
        private String wireApi = "chat";

        /** 请求超时时间（秒） */
        private int timeout = 120;

        /** 推理强度 */
        private String reasoningEffort;

        /** 是否禁用响应存储 */
        private Boolean disableResponseStorage;

        /** 最大输出 Token */
        private Integer maxOutputTokens;
    }

    @Data
    public static class QianfanCodingPlanConfig {
        /** API Key，从 QIANFAN_OPENAI_API_KEY 环境变量读取 */
        private String apiKey;

        /** 模型名称 */
        private String model = "qianfan-code-latest";

        /** API 基础地址 */
        private String baseUrl = "https://qianfan.baidubce.com/v2/coding";

        /** 线协议：chat */
        private String wireApi = "chat";

        /** 请求超时时间（秒） */
        private int timeout = 120;
    }

    /**
     * 创建智谱 ChatModel（OpenAI 兼容模式）
     *
     * <p>仅当 provider=zhipu 时创建此 bean。
     * 智谱 API 兼容 OpenAI 格式，可以直接使用 LangChain4j 的 OpenAI 模块。
     *
     * @return 智谱 ChatModel
     */
    @Bean(name = "zhipuChatModel")
    @Primary
    @ConditionalOnProperty(name = "jarvis.llm.provider", havingValue = "zhipu")
    public ChatModel zhipuChatModel() {
        log.info("[LLMConfig] 初始化智谱 ChatModel: model={}, baseUrl={}",
                zhipu.getModel(), zhipu.getBaseUrl());

        return OpenAiChatModel.builder()
                .baseUrl(zhipu.getBaseUrl())
                .apiKey(zhipu.getApiKey())
                .modelName(zhipu.getModel())
                .timeout(Duration.ofSeconds(zhipu.getTimeout()))
                .temperature(zhipu.getTemperature())
                .maxTokens(zhipu.getMaxTokens())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建智谱 StreamingChatModel（OpenAI 兼容模式）。
     */
    @Bean(name = "zhipuStreamingChatModel")
    @Primary
    @ConditionalOnProperty(name = "jarvis.llm.provider", havingValue = "zhipu")
    public StreamingChatModel zhipuStreamingChatModel() {
        log.info("[LLMConfig] 初始化智谱 StreamingChatModel: model={}, baseUrl={}",
                zhipu.getModel(), zhipu.getBaseUrl());

        return OpenAiStreamingChatModel.builder()
                .baseUrl(zhipu.getBaseUrl())
                .apiKey(zhipu.getApiKey())
                .modelName(zhipu.getModel())
                .timeout(Duration.ofSeconds(zhipu.getTimeout()))
                .temperature(zhipu.getTemperature())
                .maxTokens(zhipu.getMaxTokens())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建 GPT ChatModel（OpenAI Chat / Responses API 兼容模式）。
     */
    @Bean(name = "gptChatModel")
    @Primary
    @ConditionalOnProperty(name = "jarvis.llm.provider", havingValue = "gpt")
    public ChatModel gptChatModel() {
        log.info("[LLMConfig] 初始化 GPT ChatModel: model={}, baseUrl={}, wireApi={}, reasoningEffort={}, store={}",
                gpt.getModel(), gpt.getBaseUrl(), gpt.getWireApi(), gpt.getReasoningEffort(),
                gpt.getDisableResponseStorage() == null ? null : !gpt.getDisableResponseStorage());

        if ("chat".equalsIgnoreCase(gpt.getWireApi())) {
            var builder = OpenAiChatModel.builder()
                    .baseUrl(gpt.getBaseUrl())
                    .apiKey(gpt.getApiKey())
                    .modelName(gpt.getModel())
                    .timeout(Duration.ofSeconds(gpt.getTimeout()))
                    .logRequests(true)
                    .logResponses(true);

            if (gpt.getMaxOutputTokens() != null) {
                builder.maxTokens(gpt.getMaxOutputTokens());
            }
            if (gpt.getReasoningEffort() != null && !gpt.getReasoningEffort().isBlank()) {
                builder.reasoningEffort(gpt.getReasoningEffort());
            }
            if (gpt.getDisableResponseStorage() != null) {
                builder.store(!gpt.getDisableResponseStorage());
            }
            return builder.build();
        }

        if ("responses".equalsIgnoreCase(gpt.getWireApi())) {
            var builder = OpenAiResponsesChatModel.builder()
                    .baseUrl(gpt.getBaseUrl())
                    .apiKey(gpt.getApiKey())
                    .modelName(gpt.getModel())
                    .logRequests(true)
                    .logResponses(true);

            if (gpt.getMaxOutputTokens() != null) {
                builder.maxOutputTokens(gpt.getMaxOutputTokens());
            }
            if (gpt.getReasoningEffort() != null && !gpt.getReasoningEffort().isBlank()) {
                builder.reasoningEffort(gpt.getReasoningEffort());
            }
            if (gpt.getDisableResponseStorage() != null) {
                builder.store(!gpt.getDisableResponseStorage());
            }
            return builder.build();
        }

        throw new IllegalArgumentException("GPT provider only supports wire-api=chat or wire-api=responses");
    }

    /**
     * 创建 GPT StreamingChatModel（OpenAI Chat / Responses API 兼容模式）。
     */
    @Bean(name = "gptStreamingChatModel")
    @Primary
    @ConditionalOnProperty(name = "jarvis.llm.provider", havingValue = "gpt")
    public StreamingChatModel gptStreamingChatModel() {
        if ("chat".equalsIgnoreCase(gpt.getWireApi())) {
            var builder = OpenAiStreamingChatModel.builder()
                    .baseUrl(gpt.getBaseUrl())
                    .apiKey(gpt.getApiKey())
                    .modelName(gpt.getModel())
                    .timeout(Duration.ofSeconds(gpt.getTimeout()))
                    .logRequests(true)
                    .logResponses(true);

            if (gpt.getMaxOutputTokens() != null) {
                builder.maxTokens(gpt.getMaxOutputTokens());
            }
            return builder.build();
        }

        if ("responses".equalsIgnoreCase(gpt.getWireApi())) {
            var builder = OpenAiResponsesStreamingChatModel.builder()
                    .baseUrl(gpt.getBaseUrl())
                    .apiKey(gpt.getApiKey())
                    .modelName(gpt.getModel())
                    .logRequests(true)
                    .logResponses(true);

            if (gpt.getMaxOutputTokens() != null) {
                builder.maxOutputTokens(gpt.getMaxOutputTokens());
            }
            if (gpt.getReasoningEffort() != null && !gpt.getReasoningEffort().isBlank()) {
                builder.reasoningEffort(gpt.getReasoningEffort());
            }
            if (gpt.getDisableResponseStorage() != null) {
                builder.store(!gpt.getDisableResponseStorage());
            }
            return builder.build();
        }

        throw new IllegalArgumentException("GPT provider only supports wire-api=chat or wire-api=responses");
    }

    /**
     * 创建千帆 Coding Plan ChatModel（OpenAI Chat Completions 兼容模式）。
     */
    @Bean(name = "qianfanCodingPlanChatModel")
    @Primary
    @ConditionalOnExpression("'${jarvis.llm.provider}' == 'qianfan-coding-plan' || '${jarvis.llm.provider}' == 'Qianfan_Coding_Plan'")
    public ChatModel qianfanCodingPlanChatModel() {
        if (!"chat".equalsIgnoreCase(qianfanCodingPlan.getWireApi())) {
            throw new IllegalArgumentException("Qianfan Coding Plan provider only supports wire-api=chat");
        }

        log.info("[LLMConfig] 初始化千帆 Coding Plan ChatModel: model={}, baseUrl={}, wireApi={}",
                qianfanCodingPlan.getModel(), qianfanCodingPlan.getBaseUrl(), qianfanCodingPlan.getWireApi());

        return OpenAiChatModel.builder()
                .baseUrl(qianfanCodingPlan.getBaseUrl())
                .apiKey(qianfanCodingPlan.getApiKey())
                .modelName(qianfanCodingPlan.getModel())
                .timeout(Duration.ofSeconds(qianfanCodingPlan.getTimeout()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建千帆 Coding Plan StreamingChatModel（OpenAI Chat Completions 兼容模式）。
     */
    @Bean(name = "qianfanCodingPlanStreamingChatModel")
    @Primary
    @ConditionalOnExpression("'${jarvis.llm.provider}' == 'qianfan-coding-plan' || '${jarvis.llm.provider}' == 'Qianfan_Coding_Plan'")
    public StreamingChatModel qianfanCodingPlanStreamingChatModel() {
        if (!"chat".equalsIgnoreCase(qianfanCodingPlan.getWireApi())) {
            throw new IllegalArgumentException("Qianfan Coding Plan provider only supports wire-api=chat");
        }

        log.info("[LLMConfig] 初始化千帆 Coding Plan StreamingChatModel: model={}, baseUrl={}, wireApi={}",
                qianfanCodingPlan.getModel(), qianfanCodingPlan.getBaseUrl(), qianfanCodingPlan.getWireApi());

        return OpenAiStreamingChatModel.builder()
                .baseUrl(qianfanCodingPlan.getBaseUrl())
                .apiKey(qianfanCodingPlan.getApiKey())
                .modelName(qianfanCodingPlan.getModel())
                .timeout(Duration.ofSeconds(qianfanCodingPlan.getTimeout()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建 DashScope (Qwen) StreamingChatModel。
     *
     * <p>仅当 provider=dashscope 时创建此 bean。
     * 使用 QwenStreamingChatModel 实现真正的流式输出。
     */
    @Bean(name = "dashscopeStreamingChatModel")
    @Primary
    @ConditionalOnProperty(name = "jarvis.llm.provider", havingValue = "dashscope")
    public StreamingChatModel dashscopeStreamingChatModel() {
        log.info("[LLMConfig] 初始化 DashScope StreamingChatModel: model={}", dashscope.getModel());

        return QwenStreamingChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName(dashscope.getModel())
                .build();
    }

}
