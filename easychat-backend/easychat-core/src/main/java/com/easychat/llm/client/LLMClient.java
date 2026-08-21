package com.easychat.llm.client;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LLMClient {
    /**
     * 同步聊天
     */
    String chat(String prompt);

    default String chat(String prompt, LLMCallOptions options) {
        return chat(prompt);
    }

    default String chat(String prompt, LLMCallOptions options, List<String> images) {
        return chat(prompt, options);
    }

    /**
     * 流式聊天
     */
    Flux<String> streamChat(String prompt);

    default Flux<String> streamChat(String prompt, LLMCallOptions options) {
        return streamChat(prompt);
    }

    default Flux<String> streamChat(String prompt, LLMCallOptions options, List<String> images) {
        return streamChat(prompt, options);
    }
}
