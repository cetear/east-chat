package com.easychat.rag.embedding;

public interface EmbeddingService {
    /**
     * 文本向量化
     */
    float[] embed(String text);
}
