package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.rag.retriever.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Order(20)
public class RagRetrievalCapability implements AgentCapability {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired(required = false)
    private Retriever retriever;
    @Autowired(required = false)
    private com.easychat.rag.retriever.HttpReranker reranker;
    @Value("${easychat.rag.top-k:3}")
    private int topK = 3;
    @Value("${easychat.rag.max-context-chars:6000}")
    private int maxChars = 6000;

    public boolean supports(ChatExecutionContext context) {
        return context != null && context.isRagEnabled();
    }

    public void beforeRun(AgentRequest request, ChatExecutionContext context) {
        if (retriever == null) throw new IllegalArgumentException("RAG is disabled");
        var result = retriever.retrieve(new RetrievalRequest(request.getUserMessage(), topK, context.getDataset(), context.getUserId()));
        if (result == null) throw new IllegalStateException("RAG returned no result");
        context.setRetrievalWarning(result.warning());
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        int limit = Math.max(1, Math.min(maxChars, context.getMaxContextChars() / 2));
        var documents = result.documents();
        if (reranker != null && reranker.configured()) {
            try {
                documents = reranker.rank(request.getUserMessage(), documents);
            } catch (Exception e) {
                context.setRetrievalWarning(Objects.toString(context.getRetrievalWarning(), "") + " rerank_unavailable: using retrieval order");
            }
        }
        for (var doc : documents) {
            if (doc.getContent() == null || doc.getContent().isBlank()) continue;
            String header = "[来源: " + Objects.toString(doc.getTitle(), doc.getDocId()) + "; chunk: " + doc.getId() + "]\n";
            int room = limit - text.length() - header.length() - 2;
            if (room <= 0) break;
            text.append(header).append(doc.getContent(), 0, Math.min(room, doc.getContent().length())).append("\n\n");
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("chunkId", doc.getId());
            source.put("docId", doc.getDocId());
            source.put("title", doc.getTitle());
            source.put("pageNo", doc.getPageNo());
            sources.add(source);
        }
        context.setRetrievedContext(text.toString());
        try {
            context.setRetrievedSourcesJson(json.writeValueAsString(sources));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
