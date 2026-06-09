package com.easychat.llm.provider;

import com.easychat.llm.client.LLMCallOptions;
import reactor.core.publisher.Flux;

/**
 * LLM 渠道商抽象接口。
 * 每个渠道商实现该接口，提供同步/流式调用能力。
 */
public interface LLMProvider {

    /** 渠道标识，对应 provider.provider_code */
    String getProviderCode();

    /** 同步对话 */
    String chat(String prompt);

    default String chat(String prompt, LLMCallOptions options) {
        return chat(prompt);
    }

    /** 流式对话，逐 token 发射 */
    Flux<String> streamChat(String prompt);

    default Flux<String> streamChat(String prompt, LLMCallOptions options) {
        return streamChat(prompt);
    }

    /** 健康检查（可扩展为主动探测） */
    default boolean isHealthy() {
        return true;
    }
}
