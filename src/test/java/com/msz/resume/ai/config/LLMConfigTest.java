package com.msz.resume.ai.chat.llm.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LLMConfigTest {

    @Test
    @DisplayName("GPT wire-api=chat 时同步模型使用 OpenAiChatModel")
    void gptChatModelShouldUseOpenAiChatModelWhenWireApiIsChat() {
        LLMConfig config = new LLMConfig();
        config.setProvider("gpt");
        config.getGpt().setApiKey("test-key");
        config.getGpt().setBaseUrl("https://api.openai.com/v1");
        config.getGpt().setModel("gpt-5.4");
        config.getGpt().setWireApi("chat");

        ChatModel chatModel = config.gptChatModel();

        assertInstanceOf(OpenAiChatModel.class, chatModel);
    }

    @Test
    @DisplayName("GPT wire-api=chat 时流式模型使用 OpenAiStreamingChatModel")
    void gptStreamingChatModelShouldUseOpenAiStreamingChatModelWhenWireApiIsChat() {
        LLMConfig config = new LLMConfig();
        config.setProvider("gpt");
        config.getGpt().setApiKey("test-key");
        config.getGpt().setBaseUrl("https://api.openai.com/v1");
        config.getGpt().setModel("gpt-5.4");
        config.getGpt().setWireApi("chat");

        StreamingChatModel streamingChatModel = config.gptStreamingChatModel();

        assertInstanceOf(OpenAiStreamingChatModel.class, streamingChatModel);
    }

    @Test
    @DisplayName("GPT wire-api=responses 时同步模型使用自定义 Responses 同步适配器")
    void gptChatModelShouldUseResponsesAdapterWhenWireApiIsResponses() {
        LLMConfig config = new LLMConfig();
        config.setProvider("gpt");
        config.getGpt().setApiKey("test-key");
        config.getGpt().setBaseUrl("https://api.openai.com/v1");
        config.getGpt().setModel("gpt-5.4");
        config.getGpt().setWireApi("responses");

        ChatModel chatModel = config.gptChatModel();

        assertInstanceOf(OpenAiResponsesChatModel.class, chatModel);
    }

    @Test
    @DisplayName("GPT wire-api=responses 时流式模型使用 OpenAiResponsesStreamingChatModel")
    void gptStreamingChatModelShouldUseResponsesStreamingModelWhenWireApiIsResponses() {
        LLMConfig config = new LLMConfig();
        config.setProvider("gpt");
        config.getGpt().setApiKey("test-key");
        config.getGpt().setBaseUrl("https://api.openai.com/v1");
        config.getGpt().setModel("gpt-5.4");
        config.getGpt().setWireApi("responses");

        StreamingChatModel streamingChatModel = config.gptStreamingChatModel();

        assertInstanceOf(OpenAiResponsesStreamingChatModel.class, streamingChatModel);
    }
}
