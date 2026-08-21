package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.rag.retriever.Retriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(20)
public class RagRetrievalCapability implements AgentCapability {

    @Autowired
    private Retriever retriever;

    @Value("${easychat.rag.top-k:3}")
    private int topK;

    @Override
    public boolean supports(ChatExecutionContext context) {
        return context != null && context.isRagEnabled();
    }

    @Override
    public void beforeRun(AgentRequest request, ChatExecutionContext context) {
        if (request == null || request.getUserMessage() == null || retriever == null) {
            return;
        }
        try {
            List<Retriever.Document> docs = retriever.search(request.getUserMessage(), topK);
            if (docs == null || docs.isEmpty()) {
                return;
            }
            String retrieved = docs.stream()
                .map(Retriever.Document::getContent)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n\n"));
            context.setRetrievedContext(retrieved);
        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
        }
    }
}
