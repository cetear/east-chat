package com.easychat.llm.client.impl;

import com.easychat.llm.client.LLMClient;
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
            chatModel = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(60))
                .build();
        }
        return chatModel;
    }

    private StreamingChatLanguageModel getStreamingModel() {
        if (streamingModel == null) {
            streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(60))
                .build();
        }
        return streamingModel;
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
}
