package com.msz.resume.ai.chat.llm.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;

@Slf4j
class OpenAiResponsesChatModel implements ChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_USER_AGENT = "LangChain4j";
    private static final String OPENAI_ORGANIZATION_HEADER = "OpenAI-Organization";

    private static final String FIELD_MODEL = "model";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_STREAM = "stream";
    private static final String FIELD_STORE = "store";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_TOP_P = "top_p";
    private static final String FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String FIELD_MAX_TOOL_CALLS = "max_tool_calls";
    private static final String FIELD_PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    private static final String FIELD_PREVIOUS_RESPONSE_ID = "previous_response_id";
    private static final String FIELD_TOP_LOGPROBS = "top_logprobs";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_TOOL_CHOICE = "tool_choice";
    private static final String FIELD_TRUNCATION = "truncation";
    private static final String FIELD_INCLUDE = "include";
    private static final String FIELD_SERVICE_TIER = "service_tier";
    private static final String FIELD_SAFETY_IDENTIFIER = "safety_identifier";
    private static final String FIELD_PROMPT_CACHE_KEY = "prompt_cache_key";
    private static final String FIELD_PROMPT_CACHE_RETENTION = "prompt_cache_retention";
    private static final String FIELD_REASONING = "reasoning";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_STRICT = "strict";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_VERBOSITY = "verbosity";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_PARAMETERS = "parameters";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_OUTPUT = "output";
    private static final String FIELD_CALL_ID = "call_id";
    private static final String FIELD_TEXT_VALUE = "text";
    private static final String FIELD_IMAGE_URL = "image_url";
    private static final String FIELD_DETAIL = "detail";
    private static final String FIELD_JSON_SCHEMA = "json_schema";
    private static final String FIELD_SCHEMA = "schema";
    private static final String FIELD_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_INPUT_TOKENS = "input_tokens";
    private static final String FIELD_OUTPUT_TOKENS = "output_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_INPUT_TOKENS_DETAILS = "input_tokens_details";
    private static final String FIELD_CACHED_TOKENS = "cached_tokens";
    private static final String FIELD_SERVICE_TIER_RESPONSE = "service_tier";
    private static final String FIELD_SYSTEM_FINGERPRINT = "system_fingerprint";
    private static final String FIELD_OUTPUT_ITEMS = "output";

    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_FUNCTION_CALL = "function_call";
    private static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_OBJECT = "object";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String DETAIL_AUTO_VALUE = "auto";
    private static final String DEFAULT_IMAGE_MIME_TYPE = "image/jpeg";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String organizationId;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String previousResponseId;
    private final Integer topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean store;
    private final Boolean strict;
    private final ChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    private OpenAiResponsesChatModel(ResponsesChatModelBuilder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient delegate = httpClientBuilder.build();
        boolean logRequests = Boolean.TRUE.equals(builder.logRequests);
        boolean logResponses = Boolean.TRUE.equals(builder.logResponses);
        if (logRequests || logResponses) {
            this.httpClient = new LoggingHttpClient(delegate, logRequests, logResponses);
        } else {
            this.httpClient = delegate;
        }

        this.baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        this.apiKey = builder.apiKey;
        this.organizationId = builder.organizationId;
        this.modelName = ensureNotNull(builder.modelName, "modelName");
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.maxToolCalls = builder.maxToolCalls;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.previousResponseId = builder.previousResponseId;
        this.topLogprobs = builder.topLogprobs;
        this.truncation = builder.truncation;
        this.include = copyIfNotNull(builder.include);
        this.serviceTier = builder.serviceTier;
        this.safetyIdentifier = builder.safetyIdentifier;
        this.promptCacheKey = builder.promptCacheKey;
        this.promptCacheRetention = builder.promptCacheRetention;
        this.reasoningEffort = builder.reasoningEffort;
        this.textVerbosity = builder.textVerbosity;
        this.store = getOrDefault(builder.store, false);
        this.strict = getOrDefault(builder.strict, true);
        this.listeners = copy(builder.listeners);
        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    static ResponsesChatModelBuilder builder() {
        return new ResponsesChatModelBuilder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        try {
            Map<String, Object> payload = buildRequestPayload(chatRequest);
            HttpRequest request = buildHttpRequest(payload);
            SuccessfulHttpResponse rawResponse = httpClient.execute(request);
            return parseResponse(rawResponse);
        } catch (HttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("GPT Responses API request failed", e);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    private Map<String, Object> buildRequestPayload(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();

        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            input.addAll(toResponsesMessages(message));
        }

        Map<String, Object> payload = new HashMap<>();
        String effectiveModelName =
                parameters != null && parameters.modelName() != null ? parameters.modelName() : modelName;
        payload.put(FIELD_MODEL, effectiveModelName);
        payload.put(FIELD_INPUT, input);
        payload.put(FIELD_STREAM, false);
        payload.put(FIELD_STORE, store);

        Double effectiveTemperature =
                parameters != null && parameters.temperature() != null ? parameters.temperature() : temperature;
        if (effectiveTemperature != null) {
            payload.put(FIELD_TEMPERATURE, effectiveTemperature);
        }

        Double effectiveTopP = parameters != null && parameters.topP() != null ? parameters.topP() : topP;
        if (effectiveTopP != null) {
            payload.put(FIELD_TOP_P, effectiveTopP);
        }

        Integer requestMaxOutputTokens = parameters != null ? parameters.maxOutputTokens() : null;
        Integer effectiveMaxOutputTokens = requestMaxOutputTokens != null ? requestMaxOutputTokens : maxOutputTokens;
        if (effectiveMaxOutputTokens != null) {
            payload.put(FIELD_MAX_OUTPUT_TOKENS, effectiveMaxOutputTokens);
        }

        if (maxToolCalls != null) {
            payload.put(FIELD_MAX_TOOL_CALLS, maxToolCalls);
        }
        if (parallelToolCalls != null) {
            payload.put(FIELD_PARALLEL_TOOL_CALLS, parallelToolCalls);
        }
        if (previousResponseId != null && !previousResponseId.isBlank()) {
            payload.put(FIELD_PREVIOUS_RESPONSE_ID, previousResponseId);
        }
        if (topLogprobs != null) {
            payload.put(FIELD_TOP_LOGPROBS, topLogprobs);
        }
        if (truncation != null && !truncation.isBlank()) {
            payload.put(FIELD_TRUNCATION, truncation);
        }
        if (include != null && !include.isEmpty()) {
            payload.put(FIELD_INCLUDE, include);
        }
        if (serviceTier != null && !serviceTier.isBlank()) {
            payload.put(FIELD_SERVICE_TIER, serviceTier);
        }
        if (safetyIdentifier != null && !safetyIdentifier.isBlank()) {
            payload.put(FIELD_SAFETY_IDENTIFIER, safetyIdentifier);
        }
        if (promptCacheKey != null && !promptCacheKey.isBlank()) {
            payload.put(FIELD_PROMPT_CACHE_KEY, promptCacheKey);
        }
        if (promptCacheRetention != null && !promptCacheRetention.isBlank()) {
            payload.put(FIELD_PROMPT_CACHE_RETENTION, promptCacheRetention);
        }
        String requestReasoningEffort = parameters instanceof OpenAiChatRequestParameters openAiParameters
                ? openAiParameters.reasoningEffort()
                : null;
        String effectiveReasoningEffort = requestReasoningEffort != null && !requestReasoningEffort.isBlank()
                ? requestReasoningEffort
                : reasoningEffort;
        if (effectiveReasoningEffort != null && !effectiveReasoningEffort.isBlank()) {
            Map<String, Object> reasoning = new HashMap<>();
            reasoning.put(FIELD_EFFORT, effectiveReasoningEffort);
            payload.put(FIELD_REASONING, reasoning);
        }

        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpecification toolSpec : toolSpecifications) {
                Map<String, Object> tool = new HashMap<>();
                tool.put(FIELD_TYPE, TYPE_FUNCTION);
                tool.put(FIELD_NAME, toolSpec.name());
                if (toolSpec.description() != null) {
                    tool.put(FIELD_DESCRIPTION, toolSpec.description());
                }

                Map<String, Object> functionParameters = null;
                if (toolSpec.parameters() != null) {
                    functionParameters = toMap(toolSpec.parameters(), strict);
                } else if (strict) {
                    functionParameters = new HashMap<>();
                    functionParameters.put(FIELD_TYPE, TYPE_OBJECT);
                    functionParameters.put("properties", new HashMap<>());
                    functionParameters.put(FIELD_ADDITIONAL_PROPERTIES, false);
                }
                if (functionParameters != null) {
                    tool.put(FIELD_PARAMETERS, functionParameters);
                }
                if (strict) {
                    tool.put(FIELD_STRICT, true);
                }
                tools.add(tool);
            }
            payload.put(FIELD_TOOLS, tools);
            payload.put(FIELD_TOOL_CHOICE, chatRequest.toolChoice() != null ? switch (chatRequest.toolChoice()) {
                case AUTO -> "auto";
                case REQUIRED -> "required";
                case NONE -> "none";
            } : "auto");
        }

        Map<String, Object> textConfig = toResponseTextConfig(chatRequest.responseFormat());
        if (textVerbosity != null && !textVerbosity.isBlank()) {
            if (textConfig == null) {
                textConfig = new HashMap<>();
            }
            textConfig.put(FIELD_VERBOSITY, textVerbosity);
        }
        if (textConfig != null) {
            payload.put(FIELD_TEXT, textConfig);
        }

        return payload;
    }

    private HttpRequest buildHttpRequest(Map<String, Object> payload) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(payload);

        HttpRequest.Builder requestBuilder = HttpRequest.builder()
                .url(baseUrl + "/responses")
                .method(HttpMethod.POST)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .body(body);

        if (organizationId != null && !organizationId.isBlank()) {
            requestBuilder.addHeader(OPENAI_ORGANIZATION_HEADER, organizationId);
        }

        return requestBuilder.build();
    }

    private ChatResponse parseResponse(SuccessfulHttpResponse rawResponse) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(rawResponse.body());
        JsonNode output = root.path(FIELD_OUTPUT_ITEMS);

        StringBuilder textBuilder = new StringBuilder();
        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();

        if (output.isArray()) {
            for (JsonNode item : output) {
                String itemType = item.path(FIELD_TYPE).asText("");
                if (TYPE_MESSAGE.equals(itemType)) {
                    JsonNode content = item.path(FIELD_CONTENT);
                    if (content.isArray()) {
                        for (JsonNode contentItem : content) {
                            if (TYPE_OUTPUT_TEXT.equals(contentItem.path(FIELD_TYPE).asText(""))) {
                                textBuilder.append(contentItem.path(FIELD_TEXT_VALUE).asText(""));
                            }
                        }
                    }
                } else if (TYPE_FUNCTION_CALL.equals(itemType)) {
                    toolExecutionRequests.add(ToolExecutionRequest.builder()
                            .id(item.path(FIELD_CALL_ID).asText())
                            .name(item.path(FIELD_NAME).asText())
                            .arguments(item.path(FIELD_ARGUMENTS).asText(""))
                            .build());
                }
            }
        }

        AiMessage aiMessage;
        String text = textBuilder.isEmpty() ? null : textBuilder.toString();
        if (!toolExecutionRequests.isEmpty() && text != null) {
            aiMessage = new AiMessage(text, toolExecutionRequests);
        } else if (!toolExecutionRequests.isEmpty()) {
            aiMessage = AiMessage.from(toolExecutionRequests);
        } else {
            aiMessage = new AiMessage(text != null ? text : "");
        }

        OpenAiTokenUsage tokenUsage = null;
        JsonNode usage = root.path(FIELD_USAGE);
        if (!usage.isMissingNode()) {
            OpenAiTokenUsage.Builder usageBuilder = OpenAiTokenUsage.builder()
                    .inputTokenCount(usage.path(FIELD_INPUT_TOKENS).asInt())
                    .outputTokenCount(usage.path(FIELD_OUTPUT_TOKENS).asInt())
                    .totalTokenCount(usage.path(FIELD_TOTAL_TOKENS).asInt());

            JsonNode inputTokenDetails = usage.path(FIELD_INPUT_TOKENS_DETAILS);
            if (!inputTokenDetails.isMissingNode()) {
                int cachedTokens = inputTokenDetails.path(FIELD_CACHED_TOKENS).asInt();
                if (cachedTokens > 0) {
                    usageBuilder.inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                            .cachedTokens(cachedTokens)
                            .build());
                }
            }
            tokenUsage = usageBuilder.build();
        }

        OpenAiChatResponseMetadata.Builder metadataBuilder = OpenAiChatResponseMetadata.builder()
                .id(root.path(FIELD_ID).asText(null))
                .modelName(root.path(FIELD_MODEL).asText(null))
                .rawHttpResponse(rawResponse);

        if (tokenUsage != null) {
            metadataBuilder.tokenUsage(tokenUsage);
        }
        if (root.hasNonNull(FIELD_CREATED)) {
            metadataBuilder.created(root.path(FIELD_CREATED).asLong());
        }
        if (root.hasNonNull(FIELD_SERVICE_TIER_RESPONSE)) {
            metadataBuilder.serviceTier(root.path(FIELD_SERVICE_TIER_RESPONSE).asText());
        }
        if (root.hasNonNull(FIELD_SYSTEM_FINGERPRINT)) {
            metadataBuilder.systemFingerprint(root.path(FIELD_SYSTEM_FINGERPRINT).asText());
        }

        FinishReason finishReason = determineFinishReason(root.path(FIELD_STATUS).asText(null), !toolExecutionRequests.isEmpty());
        if (finishReason != null) {
            metadataBuilder.finishReason(finishReason);
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadataBuilder.build())
                .build();
    }

    private FinishReason determineFinishReason(String status, boolean hasToolCalls) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "completed" -> hasToolCalls ? FinishReason.TOOL_EXECUTION : FinishReason.STOP;
            case "incomplete" -> FinishReason.LENGTH;
            case "failed" -> FinishReason.OTHER;
            default -> FinishReason.OTHER;
        };
    }

    private Map<String, Object> toResponseTextConfig(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }

        Map<String, Object> textConfig = new HashMap<>();
        var jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            Map<String, Object> format = new HashMap<>();
            format.put(FIELD_TYPE, TYPE_JSON_OBJECT);
            textConfig.put(FIELD_FORMAT, format);
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema
                    || jsonSchema.rootElement() instanceof JsonRawSchema)) {
                throw new IllegalArgumentException(
                        "For OpenAI Responses API, the root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
            Map<String, Object> format = new HashMap<>();
            format.put(FIELD_TYPE, TYPE_JSON_SCHEMA);
            if (jsonSchema.name() != null) {
                format.put(FIELD_NAME, jsonSchema.name());
            }
            Map<String, Object> jsonSchemaConfig = new HashMap<>();
            jsonSchemaConfig.put(FIELD_SCHEMA, schemaMap);
            jsonSchemaConfig.put(FIELD_STRICT, strict);
            if (jsonSchema.name() != null) {
                jsonSchemaConfig.put(FIELD_NAME, jsonSchema.name());
            }
            format.put(FIELD_SCHEMA, schemaMap);
            format.put(FIELD_JSON_SCHEMA, jsonSchemaConfig);
            textConfig.put(FIELD_FORMAT, format);
        }
        return textConfig;
    }

    private List<Map<String, Object>> toResponsesMessages(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return List.of(createMessageEntry(ROLE_SYSTEM, List.of(createInputTextContent(systemMessage.text()))));
        } else if (message instanceof UserMessage userMessage) {
            List<Map<String, Object>> contentEntries = new ArrayList<>();
            for (Content content : userMessage.contents()) {
                if (content instanceof TextContent textContent) {
                    contentEntries.add(createInputTextContent(textContent.text()));
                } else if (content instanceof ImageContent imageContent) {
                    contentEntries.add(createInputImageContent(imageContent.image()));
                } else {
                    throw new UnsupportedFeatureException("Unsupported content type: "
                            + content.getClass().getName() + ". Only TextContent and ImageContent are supported.");
                }
            }
            return List.of(createMessageEntry(ROLE_USER, contentEntries));
        } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMessage) {
            List<Map<String, Object>> items = new ArrayList<>();
            if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                items.add(createMessageEntry(ROLE_ASSISTANT, List.of(createOutputTextContent(aiMessage.text()))));
            }
            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    Map<String, Object> functionCall = new HashMap<>();
                    functionCall.put(FIELD_TYPE, TYPE_FUNCTION_CALL);
                    functionCall.put(FIELD_CALL_ID, toolRequest.id());
                    functionCall.put(FIELD_NAME, toolRequest.name());
                    functionCall.put(FIELD_ARGUMENTS, toolRequest.arguments());
                    items.add(functionCall);
                }
            }
            return items;
        } else if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            if (!toolExecutionResultMessage.hasSingleText()) {
                throw new UnsupportedFeatureException(
                        "OpenAI Responses API does not support non-text content in tool results. Only text content is supported in function_call_output.");
            }
            Map<String, Object> outputEntry = new HashMap<>();
            outputEntry.put(FIELD_TYPE, TYPE_FUNCTION_CALL_OUTPUT);
            outputEntry.put(FIELD_CALL_ID, toolExecutionResultMessage.id());
            outputEntry.put(FIELD_OUTPUT, toolExecutionResultMessage.text());
            return List.of(outputEntry);
        } else {
            throw new UnsupportedFeatureException("Unsupported message type: " + message.getClass().getName());
        }
    }

    private Map<String, Object> createMessageEntry(String role, List<Map<String, Object>> contentEntries) {
        Map<String, Object> entry = new HashMap<>();
        entry.put(FIELD_TYPE, TYPE_MESSAGE);
        entry.put(FIELD_ROLE, role);
        entry.put(FIELD_CONTENT, contentEntries);
        return entry;
    }

    private Map<String, Object> createInputTextContent(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put(FIELD_TYPE, TYPE_INPUT_TEXT);
        content.put(FIELD_TEXT_VALUE, text);
        return content;
    }

    private Map<String, Object> createOutputTextContent(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put(FIELD_TYPE, TYPE_OUTPUT_TEXT);
        content.put(FIELD_TEXT_VALUE, text);
        return content;
    }

    private Map<String, Object> createInputImageContent(Image image) {
        Map<String, Object> content = new HashMap<>();
        content.put(FIELD_TYPE, TYPE_INPUT_IMAGE);
        content.put(FIELD_IMAGE_URL, buildImageUrl(image));
        content.put(FIELD_DETAIL, DETAIL_AUTO_VALUE);
        return content;
    }

    private String buildImageUrl(Image image) {
        if (image.url() != null) {
            return image.url().toString();
        } else if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : DEFAULT_IMAGE_MIME_TYPE;
            return "data:" + mimeType + ";base64," + image.base64Data();
        } else {
            throw new IllegalArgumentException("Image must have either url or base64Data");
        }
    }

    static class ResponsesChatModelBuilder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private Integer maxToolCalls;
        private Boolean parallelToolCalls;
        private String previousResponseId;
        private Integer topLogprobs;
        private String truncation;
        private List<String> include;
        private String serviceTier;
        private String safetyIdentifier;
        private String promptCacheKey;
        private String promptCacheRetention;
        private String reasoningEffort;
        private String textVerbosity;
        private Boolean store;
        private Boolean strict;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;

        ResponsesChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        ResponsesChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        ResponsesChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        ResponsesChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        ResponsesChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        ResponsesChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        ResponsesChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        ResponsesChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        ResponsesChatModelBuilder maxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
            return this;
        }

        ResponsesChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        ResponsesChatModelBuilder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        ResponsesChatModelBuilder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        ResponsesChatModelBuilder truncation(String truncation) {
            this.truncation = truncation;
            return this;
        }

        ResponsesChatModelBuilder include(List<String> include) {
            this.include = include;
            return this;
        }

        ResponsesChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        ResponsesChatModelBuilder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        ResponsesChatModelBuilder promptCacheKey(String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return this;
        }

        ResponsesChatModelBuilder promptCacheRetention(String promptCacheRetention) {
            this.promptCacheRetention = promptCacheRetention;
            return this;
        }

        ResponsesChatModelBuilder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        ResponsesChatModelBuilder textVerbosity(String textVerbosity) {
            this.textVerbosity = textVerbosity;
            return this;
        }

        ResponsesChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        ResponsesChatModelBuilder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        ResponsesChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        ResponsesChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        ResponsesChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        OpenAiResponsesChatModel build() {
            return new OpenAiResponsesChatModel(this);
        }
    }
}
