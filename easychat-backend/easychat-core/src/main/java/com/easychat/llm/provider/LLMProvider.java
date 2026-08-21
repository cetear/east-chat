package com.easychat.llm.provider;

import com.easychat.llm.client.LLMClient;

/**
 * LLM 渠道商抽象接口。
 * 每个渠道商实现该接口，提供同步/流式调用能力。
 */
public interface LLMProvider extends LLMClient {

    /** 渠道标识，对应 provider.provider_code */
    String getProviderCode();

    /** 健康检查（可扩展为主动探测） */
    default boolean isHealthy() {
        return true;
    }
}
