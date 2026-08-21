package com.easychat.tools.impl;

import com.easychat.infra.es.EsClientWrapper;
import com.easychat.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EsQueryTool implements Tool {

    private static final int DEFAULT_SIZE = 10;

    @Autowired
    private EsClientWrapper esClientWrapper;

    @Value("${easychat.es.index:easychat}")
    private String defaultIndex;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "es_search";
    }

    @Override
    public String description() {
        return "Search Elasticsearch documents by keyword. "
            + "Input JSON: {\"query\": \"keyword\", \"index\": \"optional index name\", \"topK\": 10}. "
            + "Returns matched documents in JSON.";
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            String query = asString(args.get("query"));
            if (query == null || query.isBlank()) {
                return "Error: 'query' is required.";
            }

            String index = args.get("index") != null && !asString(args.get("index")).isBlank()
                ? asString(args.get("index"))
                : defaultIndex;

            int size = parseSize(args.get("topK"), DEFAULT_SIZE);

            List<Map<String, Object>> results = esClientWrapper.search(index, query, size);
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error("ES query tool failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private int parseSize(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.toString().trim());
            return Math.max(1, Math.min(parsed, 50));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
