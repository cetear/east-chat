package com.easychat.llm.client.impl;

import com.easychat.llm.client.LLMClient;
import com.easychat.llm.client.LLMCallOptions;
import com.easychat.llm.config.LLMProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@Component
public class LangChain4jLLMClient implements LLMClient {

    @Autowired
    private LLMProperties properties;

    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingModel;

    private ChatLanguageModel getChatModel() {
        if (chatModel == null) {
            chatModel = buildChatModel(LLMCallOptions.empty());
        }
        return chatModel;
    }

    private StreamingChatLanguageModel getStreamingModel() {
        if (streamingModel == null) {
            streamingModel = buildStreamingModel(LLMCallOptions.empty());
        }
        return streamingModel;
    }

    private ChatLanguageModel buildChatModel(LLMCallOptions options) {
        return OpenAiChatModel.builder()
            .apiKey(properties.getApiKey())
            .baseUrl(properties.getBaseUrl())
            .modelName(resolveModelName(options))
            .temperature(resolveTemperature(options))
            .topP(resolveTopP(options))
            .stop(options != null ? options.getStop() : null)
            .maxTokens(resolveMaxTokens(options))
            .responseFormat(options != null ? options.getResponseFormat() : null)
            .seed(options != null ? options.getSeed() : null)
            .presencePenalty(options != null ? options.getPresencePenalty() : null)
            .frequencyPenalty(options != null ? options.getFrequencyPenalty() : null)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    private StreamingChatLanguageModel buildStreamingModel(LLMCallOptions options) {
        return OpenAiStreamingChatModel.builder()
            .apiKey(properties.getApiKey())
            .baseUrl(properties.getBaseUrl())
            .modelName(resolveModelName(options))
            .temperature(resolveTemperature(options))
            .topP(resolveTopP(options))
            .stop(options != null ? options.getStop() : null)
            .maxTokens(resolveMaxTokens(options))
            .responseFormat(options != null ? options.getResponseFormat() : null)
            .seed(options != null ? options.getSeed() : null)
            .presencePenalty(options != null ? options.getPresencePenalty() : null)
            .frequencyPenalty(options != null ? options.getFrequencyPenalty() : null)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    @Override
    public String chat(String prompt) {
        try {
            return getChatModel().generate(prompt);
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            throw new RuntimeException("LLM chat failed", e);
        }
    }

    @Override
    public String chat(String prompt, LLMCallOptions options) {
        try {
            return buildChatModel(options).generate(prompt);
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            throw new RuntimeException("LLM chat failed", e);
        }
    }

    @Override
    public Flux<String> streamChat(String prompt) {
        return Flux.create(sink -> {
            try {
                getStreamingModel().generate(prompt, new StreamingResponseHandler<AiMessage>() {
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
                        log.error("Streaming chat error", error);
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to start streaming chat", e);
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<String> streamChat(String prompt, LLMCallOptions options) {
        return Flux.create(sink -> {
            try {
                buildStreamingModel(options).generate(prompt, new StreamingResponseHandler<AiMessage>() {
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
                        log.error("Streaming chat error", error);
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to start streaming chat", e);
                sink.error(e);
            }
        });
    }

    private String resolveModelName(LLMCallOptions options) {
        return options != null && options.getModelName() != null
            ? options.getModelName()
            : properties.getModelName();
    }

    private Double resolveTemperature(LLMCallOptions options) {
        return options != null && options.getTemperature() != null
            ? options.getTemperature()
            : properties.getTemperature();
    }

    private Double resolveTopP(LLMCallOptions options) {
        return options != null && options.getTopP() != null ? options.getTopP() : null;
    }

    private Integer resolveMaxTokens(LLMCallOptions options) {
        return options != null && options.getMaxTokens() != null
            ? options.getMaxTokens()
            : properties.getMaxTokens();
    }
}
