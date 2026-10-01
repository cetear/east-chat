package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保知识库索引（easychat_kb）存在并具备正确 mapping。
 * 失败仅记录日志，不阻断应用启动（ES 可能暂不可用）。
 */
@Slf4j
@Component
@Order(1)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class EsIndexManager implements ApplicationRunner {

    @Autowired
    private ElasticsearchClient client;

    @Value("${easychat.rag.index:easychat_kb}")
    private String index;
    @Value("${easychat.rag.embedding.dimensions:1024}") private int dimensions;
    @Value("${easychat.rag.analyzer:standard}") private String analyzer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureIndex();
        } catch (Exception e) {
            log.warn("ES index ensure failed, index={}, reason={}", index, e.getMessage());
        }
    }

    public void ensureIndex() throws Exception {
        boolean exists = client.indices().exists(e -> e.index(index)).value();
        if (exists) {
            var mapping = client.indices().getMapping(g -> g.index(index)).get(index).mappings().properties();
            if (!mapping.containsKey("generation") || !mapping.get("generation").isKeyword()
                    || !mapping.containsKey("content_vector") || !mapping.get("content_vector").isDenseVector()
                    || !Integer.valueOf(dimensions).equals(mapping.get("content_vector").denseVector().dims())
                    || !mapping.containsKey("content") || !mapping.get("content").isText()
                    || !java.util.Objects.equals(analyzer, mapping.get("content").text().analyzer()))
                throw new IllegalStateException("Knowledge index mapping differs; create a new index and reingest documents");
            return;
        }
        client.indices().create(c -> c
                .index(index)
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m
                        .properties("doc_id", p -> p.keyword(k -> k))
                        .properties("generation", p -> p.keyword(k -> k))
                        .properties("chunk_id", p -> p.keyword(k -> k))
                        .properties("title", p -> p.text(t -> t.analyzer(analyzer)))
                        .properties("dataset", p -> p.keyword(k -> k))
                        .properties("heading_path", p -> p.text(t -> t.analyzer(analyzer)))
                        .properties("page_no", p -> p.integer(i -> i))
                        .properties("content", p -> p.text(t -> t.analyzer(analyzer)))
                        .properties("content_vector", p -> p.denseVector(d -> d
                                .dims(dimensions)
                                .index(true)
                                .similarity("cosine")))
                        .properties("chunk_index", p -> p.integer(i -> i))
                        .properties("token_count", p -> p.integer(i -> i))
                        .properties("created_at", p -> p.date(d -> d))
                ));
        log.info("ES index created: {}", index);
    }
}
