package com.easychat.rag.retriever.impl;

import com.easychat.infra.es.EsClientWrapper;
import com.easychat.rag.retriever.Retriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EsRetriever implements Retriever {

    @Autowired
    private EsClientWrapper esClientWrapper;

    @Value("${easychat.rag.index:easychat}")
    private String index;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Document> search(String query, int topK) {
        List<Map<String, Object>> hits = esClientWrapper.search(index, query, topK);
        return hits.stream().map(this::toDocument).toList();
    }

    private Document toDocument(Map<String, Object> hit) {
        Document doc = new Document();
        doc.setScore(0.0);
        Object content = hit.get("content");
        if (content != null) {
            doc.setContent(content.toString());
        } else {
            try {
                doc.setContent(objectMapper.writeValueAsString(hit));
            } catch (Exception e) {
                doc.setContent(hit.toString());
            }
        }
        return doc;
    }
}
