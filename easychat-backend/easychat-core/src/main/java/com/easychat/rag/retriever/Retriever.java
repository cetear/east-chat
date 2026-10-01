package com.easychat.rag.retriever;

import lombok.Data;

import java.util.List;

public interface Retriever {
    /**
     * 检索相关文档
     */
    List<Document> search(RetrievalRequest request);
    default RetrievalResult retrieve(RetrievalRequest request) { return new RetrievalResult(search(request), null); }

    /** Legacy callers search only the default dataset, never the whole index. */
    default List<Document> search(String query, int topK) {
        return search(new RetrievalRequest(query, topK, null));
    }

    @Data
    class Document {
        private String id;
        private String content;
        private double score;
        private String docId;
        private String title;
        private Integer pageNo;
        private String headingPath;
    }
}
