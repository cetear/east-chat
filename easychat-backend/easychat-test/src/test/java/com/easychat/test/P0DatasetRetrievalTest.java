package com.easychat.test;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.util.ObjectBuilder;
import com.easychat.infra.es.EsClientWrapper;
import com.easychat.rag.embedding.EmbeddingClient;
import com.easychat.rag.retriever.RetrievalRequest;
import com.easychat.rag.retriever.Retriever;
import com.easychat.rag.retriever.impl.EsRetriever;
import com.easychat.rag.retriever.impl.HybridEsRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class P0DatasetRetrievalTest {
    private EsClientWrapper wrapper;
    private ElasticsearchClient client;
    private List<SearchRequest> requests;

    @BeforeEach
    void setUp() throws Exception {
        wrapper = new EsClientWrapper();
        client = mock(ElasticsearchClient.class);
        TestSupport.setField(wrapper, "client", client);
                requests = new ArrayList<>();
        TestSupport.setField(wrapper, "ragIndex", "kb");
        var mapper = mock(com.easychat.infra.mysql.mapper.KnowledgeDocMapper.class);
        var doc = new com.easychat.common.entity.KnowledgeDocDO();
        doc.setDocCode("doc"); doc.setIndexVersion("v1");
        when(mapper.selectList(any())).thenReturn(List.of(doc));
        TestSupport.setField(wrapper, "knowledgeDocMapper", mapper);
        // The fake server honors the actual filter sent by the wrapper, not a caller-side post-filter.
        when(client.search(org.mockito.ArgumentMatchers
                .<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(), eq(Object.class)))
                .thenAnswer(invocation -> {
                    Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> build = invocation.getArgument(0);
                    SearchRequest request = build.apply(new SearchRequest.Builder()).build();
                    requests.add(request);
                    var filter = request.query().isKnn()
                            ? request.query().knn().filter().get(0)
                            : request.query().bool().filter().get(0);
                                        assertEquals("dataset", filter.term().field());
                    var generation = request.query().isKnn() ? request.query().knn().filter().get(1) : request.query().bool().filter().get(1);
                    assertEquals("generation", generation.terms().field());
                    assertEquals("doc:v1", generation.terms().terms().value().get(0).stringValue());
                    String scope = filter.term().value().stringValue();
                    var response = new SearchResponse.Builder<Object>()
                            .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0));
                    response.hits(h -> {
                        h.hits(List.of());
                        for (String dataset : List.of("default", "alpha", "beta")) {
                            if (dataset.equals(scope)) {
                                h.hits(hit -> hit.index("kb").id(dataset).score(1.0)
                                        .source(Map.of("dataset", dataset, "content", dataset + "-only",
                                                "chunk_id", dataset, "doc_id", dataset)));
                            }
                        }
                        return h;
                    });
                    return response.build();
                });
    }

    private Retriever retriever(boolean hybrid) {
        if (!hybrid) {
            EsRetriever retriever = new EsRetriever();
            TestSupport.setField(retriever, "esClientWrapper", wrapper);
            TestSupport.setField(retriever, "index", "kb");
            return retriever;
        }
        HybridEsRetriever retriever = new HybridEsRetriever();
        EmbeddingClient embedding = mock(EmbeddingClient.class);
        when(embedding.embed(anyString())).thenReturn(new float[]{1, 0});
        TestSupport.setField(retriever, "esClientWrapper", wrapper);
        TestSupport.setField(retriever, "embeddingClient", embedding);
        TestSupport.setField(retriever, "index", "kb");
        TestSupport.setField(retriever, "dimensions", 2); TestSupport.setField(retriever, "bm25Size", 20);
        TestSupport.setField(retriever, "knnSize", 20);
        return retriever;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothRetrieversKeepDatasetsSeparateAndDefaultIsNotGlobal(boolean hybrid) {
        Retriever retriever = retriever(hybrid);
        for (String dataset : List.of("alpha", "beta")) {
            List<Retriever.Document> docs = retriever.search(new RetrievalRequest("same query", 3, dataset));
            assertEquals(List.of(dataset + "-only"), docs.stream().map(Retriever.Document::getContent).toList());
        }
        assertEquals(List.of("default-only"), retriever.search("same query", 3).stream()
                .map(Retriever.Document::getContent).toList());
        assertTrue(retriever.search(new RetrievalRequest("same query", 3, "missing")).isEmpty());
        assertEquals(hybrid ? 8 : 4, requests.size());
        if (hybrid) {
            assertEquals(4, requests.stream().filter(r -> r.query().isKnn()).count());
        }
    }

    @Test
    void invalidScopeCannotFallBackToAnUnfilteredQuery() {
        assertThrows(IllegalArgumentException.class, () -> new RetrievalRequest("query", 3, "*"));
        assertThrows(IllegalArgumentException.class, () -> wrapper.matchSearch("kb", "content", "q", 3, "../x"));
        assertThrows(IllegalArgumentException.class, () -> wrapper.knnSearch("kb", "content_vector", new float[]{1}, 3, "*"));
        assertThrows(IllegalArgumentException.class, () -> wrapper.hybridSearch("kb", "q", new float[]{1}, 3, 3, 3, "*"));
        verifyNoInteractions(client);
    }
}
