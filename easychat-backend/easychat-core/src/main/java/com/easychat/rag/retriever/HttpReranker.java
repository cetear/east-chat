package com.easychat.rag.retriever;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Component
public class HttpReranker {
    @org.springframework.beans.factory.annotation.Autowired private com.easychat.integration.JsonHttpService http;
    @Value("${easychat.rag.rerank.url:}") private String endpoint;
    @Value("${easychat.rag.rerank.token:}") private String token;
    public boolean configured() {return endpoint!=null&&!endpoint.isBlank();}
    public List<Retriever.Document> rank(String query,List<Retriever.Document> documents) {
        if(documents.isEmpty() || !configured()) return documents;
        var result=http.post(endpoint,token,Map.of("query",query,"documents",documents.stream().map(Retriever.Document::getContent).toList()));
        var indexes=result.path("indices");
        if(!indexes.isArray() || indexes.size()!=documents.size()) throw new IllegalStateException("Reranker must return a permutation of indices");
        var seen=new HashSet<Integer>();var ranked=new ArrayList<Retriever.Document>();
        for(var index:indexes) {
            if(!index.isIntegralNumber() || index.asInt()<0 || index.asInt()>=documents.size() || !seen.add(index.asInt())) throw new IllegalStateException("Invalid reranker index");
            ranked.add(documents.get(index.asInt()));
        }
        return ranked;
    }
}