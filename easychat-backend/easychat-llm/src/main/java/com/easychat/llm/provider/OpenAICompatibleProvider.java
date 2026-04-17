package com.easychat.llm.provider;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 基于 LangChain4j 的 OpenAI 兼容渠道实现。
 * 支持任何兼容 OpenAI 协议的接口（DeepSeek、Azure OpenAI、本地模型等）。
 */
@Slf4j
public class OpenAICompatibleProvider implements LLMProvider {

    private final String providerCode;
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingModel;

    public OpenAICompatibleProvider(String providerCode,
                                    String apiKey,
                                    String baseUrl,
                                    String modelName,
                                    double temperature,
                                    int maxTokens,
                                    int timeoutMs) {
        this.providerCode = providerCode;

        Duration timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 60_000);

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();

        this.streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }

    @Override
    public String getProviderCode() {
        return providerCode;
    }

    @Override
    public String chat(String prompt) {
        return chatModel.generate(prompt);
    }

    @Override
    public Flux<String> streamChat(String prompt) {
        return Flux.create(sink -> streamingModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                sink.next(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                sink.complete();
            }

            @Override
            public void onError(Throwable error) {
                log.warn("[{}] Streaming error: {}", providerCode, error.getMessage());
                sink.error(error);
            }
        }));
    }
}
