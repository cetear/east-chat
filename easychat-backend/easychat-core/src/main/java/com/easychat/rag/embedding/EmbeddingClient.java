package com.easychat.rag.embedding;

import java.util.List;

public interface EmbeddingClient {

    /**
     * 批量向量化，返回与输入顺序一致的向量数组。
     */
    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
