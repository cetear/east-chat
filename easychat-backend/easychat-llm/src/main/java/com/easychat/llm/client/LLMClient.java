package com.easychat.llm.client;

import reactor.core.publisher.Flux;

public interface LLMClient {
    /**
     * 同步聊天
     */
    String chat(String prompt);

    /**
     * 流式聊天
     */
    Flux<String> streamChat(String prompt);
}
