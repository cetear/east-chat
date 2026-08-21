package com.easychat.core.router;

import com.easychat.llm.client.LLMClient;
import com.easychat.llm.config.LLMProperties;
import com.easychat.llm.provider.OpenAICompatibleProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM 路由配置：
 * - {@code fallbackLLMClient} 作为单渠道降级客户端（基于默认配置的 OpenAI 兼容实现）
 * - {@code modelRouter} 作为主客户端，注入到 ReActAgent 等消费方
 */
@Configuration
public class RouterConfig {

    /**
     * 默认兜底客户端，使用 application.yml 中的 easychat.llm 配置。
     */
    @Bean
    public LLMClient fallbackLLMClient(LLMProperties properties) {
        return new OpenAICompatibleProvider(
                "default",
                properties.getApiKey(),
                properties.getBaseUrl(),
                properties.getModelName(),
                properties.getTemperature(),
                null,
                properties.getMaxTokens(),
                60_000
        );
    }

    /**
     * 多渠道路由器，标记为 @Primary，替换所有 @Autowired LLMClient 注入点。
     */
    @Bean
    @Primary
    public ModelRouter modelRouter(ProviderRegistry registry,
                                   @Qualifier("fallbackLLMClient") LLMClient fallback) {
        return new ModelRouter(registry, fallback);
    }
}
