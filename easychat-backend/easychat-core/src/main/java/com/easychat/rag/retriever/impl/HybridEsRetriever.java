package com.easychat.rag.retriever.impl;
import com.easychat.infra.es.EsClientWrapper;
import com.easychat.rag.embedding.EmbeddingClient;
import com.easychat.rag.retriever.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import java.util.*;
@Component @Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${easychat.es.enabled:true} && '${easychat.rag.mode:bm25}' == 'hybrid'")
public class HybridEsRetriever implements Retriever {
    @Autowired private EsClientWrapper esClientWrapper;
    @Autowired private EmbeddingClient embeddingClient;
    @Value("${easychat.rag.index:easychat_kb}") private String index;
    @Value("${easychat.rag.hybrid.bm25-size:20}") private int bm25Size=20;
    @Value("${easychat.rag.hybrid.knn-size:20}") private int knnSize=20;
    @Value("${easychat.rag.embedding.dimensions:1024}") private int dimensions=1024;
    public List<Document> search(RetrievalRequest request) { return retrieve(request).documents(); }
    public RetrievalResult retrieve(RetrievalRequest request) {
        try {
            float[] vector=embeddingClient.embed(request.query());
            if (vector.length!=dimensions) throw new IllegalArgumentException("Embedding dimension mismatch");
            return new RetrievalResult(esClientWrapper.hybridSearch(index,request.query(),vector,bm25Size,knnSize,request.topK(),request.dataset(),request.ownerId())
                .stream().map(EsRetriever::document).toList(),null);
        } catch (Exception e) {
            var docs=esClientWrapper.matchSearch(index,"content",request.query(),request.topK(),request.dataset(),request.ownerId()).stream().map(EsRetriever::document).toList();
            return new RetrievalResult(docs,"hybrid_unavailable: using keyword retrieval");
        }
    }
}
