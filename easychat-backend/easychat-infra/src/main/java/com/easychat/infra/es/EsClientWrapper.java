package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.easychat.common.util.DatasetId;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class EsClientWrapper {
    public String clusterName() throws java.io.IOException { return client.info().clusterName(); }

    @Autowired
    private ElasticsearchClient client;
    @Autowired private com.easychat.infra.mysql.mapper.KnowledgeDocMapper knowledgeDocMapper;
    @org.springframework.beans.factory.annotation.Value("${easychat.es.index:easychat}") private String defaultIndex = "easychat";
    @org.springframework.beans.factory.annotation.Value("${easychat.rag.index:easychat_kb}") private String ragIndex = "easychat_kb";

    private void requireIndex(String index) {
        if (!java.util.Objects.equals(index, defaultIndex) && !java.util.Objects.equals(index, ragIndex))
            throw new IllegalArgumentException("Index is not allowed");
    }
    private void requireSize(int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be 1-100");
    }
    private List<FieldValue> activeGenerations(String dataset, String ownerId) {
        return knowledgeDocMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.easychat.common.entity.KnowledgeDocDO>()
                .eq(com.easychat.common.entity.KnowledgeDocDO::getDataset, dataset).eq(com.easychat.common.entity.KnowledgeDocDO::getOwnerId, ownerId)
                .eq(com.easychat.common.entity.KnowledgeDocDO::getStatus, com.easychat.common.constant.DocumentStatus.INDEXED))
            .stream().filter(doc -> doc.getIndexVersion() != null)
            .map(doc -> FieldValue.of(doc.getDocCode() + ":" + doc.getIndexVersion())).toList();
    }

    public <T> SearchResponse<T> search(String index, String query, Class<T> clazz) {
        requireIndex(index);
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
        requireIndex(index); requireSize(size);
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
        requireIndex(index); requireSize(size);
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
        requireIndex(index);
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

    /**
     * 批量写入文档，使用每个文档中的 chunk_id 作为文档 _id。
     */
    public void bulkIndex(String index, List<Map<String, Object>> documents) {
        requireIndex(index);
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder builder = new BulkRequest.Builder().refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor);
            for (Map<String, Object> document : documents) {
                Object id = document.get("chunk_id");
                builder.operations(op -> op.index(idx -> idx
                        .index(index)
                        .id(id != null ? id.toString() : null)
                        .document(document)));
            }
            BulkResponse response = client.bulk(builder.build());
            if (response.errors()) {
                List<String> failedIds = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.id() + ": " + item.error().reason())
                        .collect(Collectors.toList());
                log.warn("ES bulk index partial failure: {}", failedIds);
                throw new RuntimeException("ES bulk index failed: " + failedIds);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES bulk index failed", e);
            throw new RuntimeException("ES bulk index failed", e);
        }
    }

    /**
     * 按字段精确匹配删除文档（如按 doc_id 删除该文档全部分块）。
     */
    public void deleteByQuery(String index, String field, String value) {
        requireIndex(index);
        try {
            DeleteByQueryResponse response = client.deleteByQuery(d -> d
                    .index(index)
                    .query(q -> q.term(t -> t.field(field).value(FieldValue.of(value))))
                    .refresh(true));
            if (response.timedOut() || !response.failures().isEmpty() || response.versionConflicts() > 0)
                throw new IllegalStateException("ES deletion did not complete");
            log.info("ES deleteByQuery {}={} deleted {} docs", field, value, response.deleted());
        } catch (Exception e) {
            log.error("ES deleteByQuery failed", e);
            throw new RuntimeException("ES deleteByQuery failed", e);
        }
    }

    /**
     * 关键词召回（BM25），返回带分数与 _id 的命中。
     */
    public List<SearchHit> matchSearch(String index,String field,String query,int size,String dataset) {return matchSearch(index,field,query,size,dataset,"demo");}
    public List<SearchHit> matchSearch(String index, String field, String query, int size, String dataset, String ownerId) {
        requireIndex(index); requireSize(size);
        String scope = DatasetId.normalize(dataset);
        List<FieldValue> generations = activeGenerations(scope, ownerId);
        if (generations.isEmpty()) return List.of();
        try {
            SearchResponse<Object> response = client.search(s -> s
                            .index(index)
                            .query(q -> q.bool(b -> b
                                    .must(m -> m.match(match -> match.field(field).query(query)))
                                    .filter(f -> f.term(t -> t.field("dataset").value(scope)))
                                    .filter(f -> f.terms(t -> t.field("generation").terms(v -> v.value(generations))))))
                            .size(size),
                    Object.class);
            return toHits(response);
        } catch (Exception e) {
            log.error("ES match search failed", e);
            throw new RuntimeException("ES match search failed", e);
        }
    }

    /**
     * 向量召回（kNN），返回带分数与 _id 的命中。
     */
    public List<SearchHit> knnSearch(String index,String field,float[] vector,int size,String dataset) {return knnSearch(index,field,vector,size,dataset,"demo");}
    public List<SearchHit> knnSearch(String index, String field, float[] vector, int size, String dataset, String ownerId) {
        requireIndex(index); requireSize(size);
        String scope = DatasetId.normalize(dataset);
        List<FieldValue> generations = activeGenerations(scope, ownerId);
        if (generations.isEmpty()) return List.of();
        try {
            SearchResponse<Object> response = client.search(s -> s
                            .index(index)
                            .query(q -> q.knn(k -> k
                                    .field(field)
                                    .queryVector(toFloatList(vector))
                                    .filter(f -> f.term(t -> t.field("dataset").value(scope)))
                                    .filter(f -> f.terms(t -> t.field("generation").terms(v -> v.value(generations))))
                                    .k(size)
                                    .numCandidates(Math.max(size * 2, 50))))
                            .size(size),
                    Object.class);
            return toHits(response);
        } catch (Exception e) {
            log.error("ES knn search failed", e);
            throw new RuntimeException("ES knn search failed", e);
        }
    }

    /**
     * 混合检索：BM25 + kNN 双路召回后使用 RRF 融合排序。
     */
    public List<SearchHit> hybridSearch(String index,String query,float[] vector,int bm25Size,int knnSize,int topK,String dataset) {return hybridSearch(index,query,vector,bm25Size,knnSize,topK,dataset,"demo");}
    public List<SearchHit> hybridSearch(String index, String query, float[] vector,
                                        int bm25Size, int knnSize, int topK, String dataset, String ownerId) {
        String scope = DatasetId.normalize(dataset);
        List<SearchHit> bm25Hits = matchSearch(index, "content", query, bm25Size, scope, ownerId);
        List<SearchHit> knnHits = knnSearch(index, "content_vector", vector, knnSize, scope, ownerId);

        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sources = new HashMap<>();

        int rank = 1;
        for (SearchHit hit : bm25Hits) {
            accumulateRrf(scores, sources, hit, rank++);
        }
        rank = 1;
        for (SearchHit hit : knnHits) {
            accumulateRrf(scores, sources, hit, rank++);
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(entry -> {
                    SearchHit hit = new SearchHit();
                    hit.setId(entry.getKey());
                    hit.setScore(entry.getValue());
                    hit.setSource(sources.get(entry.getKey()));
                    return hit;
                })
                .collect(Collectors.toList());
    }

    private static final double RRF_K = 60.0;

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new java.util.ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private void accumulateRrf(Map<String, Double> scores, Map<String, Map<String, Object>> sources,
                               SearchHit hit, int rank) {
        String key = hit.getId() != null ? hit.getId() : String.valueOf(hit.getSource().get("chunk_id"));
        scores.merge(key, 1.0 / (RRF_K + rank), Double::sum);
        sources.putIfAbsent(key, hit.getSource());
    }

    private List<SearchHit> toHits(SearchResponse<Object> response) {
        return response.hits().hits().stream().map(hit -> {
            SearchHit searchHit = new SearchHit();
            searchHit.setId(hit.id());
            searchHit.setScore(hit.score() != null ? hit.score() : 0.0);
            searchHit.setSource((Map<String, Object>) hit.source());
            return searchHit;
        }).collect(Collectors.toList());
    }

    @Data
    public static class SearchHit {
        private String id;
        private double score;
        private Map<String, Object> source;
    }
}
