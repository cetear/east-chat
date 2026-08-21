package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EsClientWrapper {

    @Autowired
    private ElasticsearchClient client;

    public <T> SearchResponse<T> search(String index, String query, Class<T> clazz) {
        try {
            return client.search(s -> s
                .index(index)
                .query(q -> q
                    .match(m -> m
                        .field("content")
                        .query(query)
                    )
                ),
                clazz
            );
        } catch (Exception e) {
            log.error("ES search failed", e);
            throw new RuntimeException("ES search failed", e);
        }
    }

    public List<Map<String, Object>> search(String index, String query, int size) {
        try {
            SearchResponse<Object> response = client.search(s -> s
                    .index(index)
                    .query(q -> q
                        .match(m -> m
                            .field("content")
                            .query(query)
                        )
                    )
                    .size(size),
                Object.class
            );
            return response.hits().hits().stream()
                .map(hit -> (Map<String, Object>) hit.source())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES search failed", e);
            throw new RuntimeException("ES search failed", e);
        }
    }

    public List<Map<String, Object>> searchAll(String index, int size) {
        try {
            SearchResponse<Object> response = client.search(s -> s
                    .index(index)
                    .query(q -> q.matchAll(m -> m))
                    .size(size),
                Object.class
            );
            return response.hits().hits().stream()
                .map(hit -> (Map<String, Object>) hit.source())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES search all failed", e);
            throw new RuntimeException("ES search all failed", e);
        }
    }

    public String indexDocument(String index, String id, Map<String, Object> document) {
        try {
            IndexResponse response = client.index(i -> {
                i.index(index);
                if (StringUtils.hasText(id)) {
                    i.id(id);
                }
                return i.document(document);
            });
            return response.result().jsonValue();
        } catch (Exception e) {
            log.error("ES index document failed", e);
            throw new RuntimeException("ES index document failed", e);
        }
    }
}
