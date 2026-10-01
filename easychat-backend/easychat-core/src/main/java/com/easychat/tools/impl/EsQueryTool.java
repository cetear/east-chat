package com.easychat.tools.impl;
import com.easychat.infra.es.EsClientWrapper;
import com.easychat.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class EsQueryTool implements Tool {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private EsClientWrapper esClientWrapper;
    @Value("${easychat.rag.index:easychat_kb}") private String index;
    public String name() { return "es_search"; }
    public String description() { return "Search the selected knowledge dataset. Arguments: query, optional topK (1-50)."; }
    public Map<String,Object> parameters() { return Map.of("type","object","required",java.util.List.of("query"),
        "properties",Map.of("query",Map.of("type","string"),"topK",Map.of("type","integer","minimum",1,"maximum",50))); }
    public String execute(Map<String,Object> args) { throw new IllegalArgumentException("Trusted dataset context required"); }
    public String execute(Map<String,Object> args,ToolContext context) {
        if(context==null) throw new IllegalArgumentException("Dataset context required");
        if(args.containsKey("index") || args.containsKey("dataset")) throw new IllegalArgumentException("Tool cannot override retrieval scope");
        String query=java.util.Objects.toString(args.get("query"),"");
        if(query.isBlank()) throw new IllegalArgumentException("query is required");
        int count=args.get("topK")==null?10:Integer.parseInt(args.get("topK").toString());
        if(count<1 || count>50) throw new IllegalArgumentException("topK must be 1-50");
        try { return json.writeValueAsString(esClientWrapper.matchSearch(index,"content",query,count,context.dataset(),context.ownerId())); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
