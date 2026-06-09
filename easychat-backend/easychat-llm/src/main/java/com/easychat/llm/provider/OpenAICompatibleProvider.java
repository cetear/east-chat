package com.easychat.llm.provider;

import com.easychat.llm.client.LLMCallOptions;
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
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Duration timeout;
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingModel;

    public OpenAICompatibleProvider(String providerCode,
                                    String apiKey,
                                    String baseUrl,
                                    String modelName,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    int timeoutMs) {
        this.providerCode = providerCode;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 60_000);

        this.chatModel = buildChatModel(null);
        this.streamingModel = buildStreamingModel(null);
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
    public String chat(String prompt, LLMCallOptions options) {
        return buildChatModel(options).generate(prompt);
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

    @Override
    public Flux<String> streamChat(String prompt, LLMCallOptions options) {
        StreamingChatLanguageModel model = buildStreamingModel(options);
        return Flux.create(sink -> model.generate(prompt, new StreamingResponseHandler<AiMessage>() {
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

    private ChatLanguageModel buildChatModel(LLMCallOptions options) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(resolveModelName(options))
                .temperature(resolveTemperature(options))
                .topP(resolveTopP(options))
                .stop(options != null ? options.getStop() : null)
                .maxTokens(resolveMaxTokens(options))
                .responseFormat(options != null ? options.getResponseFormat() : null)
                .seed(options != null ? options.getSeed() : null)
                .presencePenalty(options != null ? options.getPresencePenalty() : null)
                .frequencyPenalty(options != null ? options.getFrequencyPenalty() : null)
                .timeout(timeout)
                .build();
    }

    private StreamingChatLanguageModel buildStreamingModel(LLMCallOptions options) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(resolveModelName(options))
                .temperature(resolveTemperature(options))
                .topP(resolveTopP(options))
                .stop(options != null ? options.getStop() : null)
                .maxTokens(resolveMaxTokens(options))
                .responseFormat(options != null ? options.getResponseFormat() : null)
                .seed(options != null ? options.getSeed() : null)
                .presencePenalty(options != null ? options.getPresencePenalty() : null)
                .frequencyPenalty(options != null ? options.getFrequencyPenalty() : null)
                .timeout(timeout)
                .build();
    }

    private String resolveModelName(LLMCallOptions options) {
        return options != null && options.getModelName() != null ? options.getModelName() : modelName;
    }

    private Double resolveTemperature(LLMCallOptions options) {
        return options != null && options.getTemperature() != null ? options.getTemperature() : temperature;
    }

    private Double resolveTopP(LLMCallOptions options) {
        return options != null && options.getTopP() != null ? options.getTopP() : topP;
    }

    private Integer resolveMaxTokens(LLMCallOptions options) {
        return options != null && options.getMaxTokens() != null ? options.getMaxTokens() : maxTokens;
    }
}
