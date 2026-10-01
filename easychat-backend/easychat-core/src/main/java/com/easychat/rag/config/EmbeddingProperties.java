package com.easychat.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "easychat.rag.embedding")
public class EmbeddingProperties {
    private String baseUrl = "https://api.siliconflow.cn/v1";
    private String apiKey;
    private String modelName = "BAAI/bge-m3";
    private Integer batchSize = 16;
    private Integer dimensions = 1024;
}
