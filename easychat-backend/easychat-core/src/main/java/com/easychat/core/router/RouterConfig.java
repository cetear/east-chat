package com.easychat.core.router;

import com.easychat.llm.client.LLMClient;
import com.easychat.llm.client.impl.LangChain4jLLMClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM 路由配置：
 * - {@code langchain4jLLMClient} 作为单渠道降级客户端（带限定符）
 * - {@code modelRouter} 作为主客户端，注入到 ReActAgent 等消费方
 */
@Configuration
public class RouterConfig {

    /**
     * 原始单渠道客户端，作为降级兜底。
     * 限定符为 "langchain4j"，防止与 ModelRouter 冲突。
     */
    @Bean("langchain4j")
    public LLMClient langchain4jLLMClient(LangChain4jLLMClient impl) {
        return impl;
    }

    /**
     * 多渠道路由器，标记为 @Primary，替换所有 @Autowired LLMClient 注入点。
     */
    @Bean
    @Primary
    public ModelRouter modelRouter(ProviderRegistry registry,
                                   @Qualifier("langchain4j") LLMClient fallback) {
        return new ModelRouter(registry, fallback);
    }
}
