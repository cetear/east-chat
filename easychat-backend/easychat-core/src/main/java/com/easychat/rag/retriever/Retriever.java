package com.easychat.rag.retriever;

import lombok.Data;

import java.util.List;

public interface Retriever {
    /**
     * 检索相关文档
     */
    List<Document> search(String query, int topK);

    @Data
    class Document {
        private String id;
        private String content;
        private double score;
    }
}
