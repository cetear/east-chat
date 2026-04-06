package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(ElasticsearchClient.class)
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
}
