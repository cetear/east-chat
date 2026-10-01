package com.easychat.rag.retriever.impl;
import com.easychat.infra.es.EsClientWrapper;
import com.easychat.rag.retriever.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;
@Component
@ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class EsRetriever implements Retriever {
    @Autowired private EsClientWrapper esClientWrapper;
    @Value("${easychat.rag.index:easychat_kb}") private String index;
    public List<Document> search(RetrievalRequest request) {
        return esClientWrapper.matchSearch(index,"content",request.query(),request.topK(),request.dataset(),request.ownerId()).stream().map(EsRetriever::document).toList();
    }
    public static Document document(EsClientWrapper.SearchHit hit) {
        var source=hit.getSource();
        Document doc=new Document();
        doc.setId(Objects.toString(source.get("chunk_id"),hit.getId()));
        doc.setDocId(Objects.toString(source.get("doc_id"),hit.getId()));
        doc.setContent(Objects.toString(source.get("content"),""));
        doc.setTitle(Objects.toString(source.get("title"),doc.getDocId()));
        doc.setHeadingPath(Objects.toString(source.get("heading_path"),""));
        if (source.get("page_no") instanceof Number page) doc.setPageNo(page.intValue());
        doc.setScore(hit.getScore()); return doc;
    }
}
