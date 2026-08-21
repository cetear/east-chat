package com.easychat.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "easychat.llm")
public class LLMProperties {
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String modelName = "gpt-3.5-turbo";
    private Double temperature = 0.7;
    private Integer maxTokens = 2000;
}
