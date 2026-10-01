package com.easychat.rag.embedding;

import com.easychat.rag.config.EmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容的 embedding 实现（BGE-M3 等），独立于聊天模型配置。
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${easychat.es.enabled:true} && '${easychat.rag.mode:bm25}' == 'hybrid'")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel model;

    public OpenAiEmbeddingClient(EmbeddingProperties properties) {
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        Response<List<Embedding>> response = model.embedAll(segments);
        return response.content().stream().map(Embedding::vector).toList();
    }
}
