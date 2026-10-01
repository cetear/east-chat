package com.easychat.test;
import com.easychat.infra.es.*;
import com.easychat.common.entity.KnowledgeDocDO;
import com.easychat.infra.mysql.mapper.KnowledgeDocMapper;
import com.easychat.rag.config.EmbeddingProperties;
import com.easychat.rag.dataset.KnowledgeService;
import com.easychat.rag.embedding.EmbeddingClient;
import com.easychat.rag.pipeline.*;
import com.easychat.rag.pipeline.chunk.TextChunker;
import com.easychat.rag.pipeline.model.*;
import com.easychat.rag.pipeline.parser.DocumentParser;
import com.easychat.rag.retriever.*;
import com.easychat.rag.retriever.impl.HybridEsRetriever;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class P1KnowledgeTest {
    DocumentIngestionService service; KnowledgeDocMapper mapper; EsClientWrapper es; KnowledgeDocDO doc; TextChunker chunker; DocumentParser parser;
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), KnowledgeDocDO.class);
        service=new DocumentIngestionService();mapper=mock(KnowledgeDocMapper.class);es=mock(EsClientWrapper.class);parser=mock(DocumentParser.class);chunker=mock(TextChunker.class);
        doc=new KnowledgeDocDO();doc.setId(1L);doc.setDocCode("d");doc.setDataset("alpha");doc.setStatus("PENDING");doc.setFilePath("sample.txt");doc.setFileType("txt");
        when(mapper.selectOne(any())).thenReturn(doc);when(mapper.update(isNull(),any())).thenReturn(1);
        when(parser.parse(any(),any())).thenReturn(List.of(new TextBlock("hello",1,"title")));
        when(chunker.chunk(anyList())).thenReturn(List.of(new Chunk("hello",1,"title",0),new Chunk("world",1,"title",1)));
        TestSupport.setField(service,"documents",new com.easychat.infra.mysql.repository.KnowledgeStore(mapper));TestSupport.setField(service,"esClientWrapper",es);
        TestSupport.setField(service,"documentParser",parser);TestSupport.setField(service,"textChunker",chunker);
        TestSupport.setField(service,"embeddingProperties",new EmbeddingProperties());TestSupport.setField(service,"indexManager",mock(EsIndexManager.class));
        TestSupport.setField(service,"locks",new DocumentOperationLock());TestSupport.setField(service,"index","kb");
    }
    @Test void onlyPublishAfterAllBatchesAndEachChunkHasGeneration() {
        var props=new EmbeddingProperties();props.setBatchSize(1);TestSupport.setField(service,"embeddingProperties",props);
        var batches=new ArrayList<List<Map<String,Object>>>();
        doAnswer(i->{assertNotEquals("INDEXED",doc.getStatus());batches.add(i.getArgument(1));return null;}).when(es).bulkIndex(eq("kb"),anyList());
        service.ingest("d"); assertEquals("INDEXED",doc.getStatus());assertEquals(2,doc.getChunkCount());assertEquals(2,batches.size());
        assertEquals("d:"+doc.getIndexVersion(),batches.get(0).get(0).get("generation"));
        assertFalse(batches.get(0).get(0).containsKey("content_vector"));
    }
    @Test void partialBulkFailureNeverPublishesAndCleansUp() {
        var props=new EmbeddingProperties();props.setBatchSize(1);TestSupport.setField(service,"embeddingProperties",props);
        doNothing().doThrow(new IllegalStateException("bulk failed")).when(es).bulkIndex(eq("kb"),anyList());
        service.ingest("d");assertEquals("FAILED",doc.getStatus());verify(es).deleteByQuery("kb","generation","d:"+doc.getIndexVersion());
    }
    @Test void emptyTextAndWrongEmbeddingCountFail() {
        when(chunker.chunk(anyList())).thenReturn(List.of());service.ingest("d");assertEquals("FAILED",doc.getStatus());verify(es,never()).bulkIndex(anyString(),anyList());
        when(chunker.chunk(anyList())).thenReturn(List.of(new Chunk("x",null,null,0)));
        var embedding=mock(EmbeddingClient.class);when(embedding.embed(anyList())).thenReturn(List.of());TestSupport.setField(service,"embeddingClient",embedding);
        service.ingest("d");assertEquals("FAILED",doc.getStatus());verify(es,never()).bulkIndex(anyString(),anyList());
    }
    @Test void conditionalClaimAndRejectedQueueDoNotStartWork() {
        when(mapper.update(isNull(),any())).thenReturn(0);service.ingest("d");verifyNoInteractions(parser,chunker,es);
        Executor full=r->{throw new RejectedExecutionException();};TestSupport.setField(service,"executor",full);
        assertDoesNotThrow(()->service.ingestAsync("d"));assertEquals("PENDING",doc.getStatus());
        when(mapper.selectList(any())).thenReturn(List.of(doc));assertEquals(1,service.submitPending());
    }
    @Test void failedDeletionRetainsMetadataAndCanBeRetried() {
        var knowledge=new KnowledgeService();TestSupport.setField(knowledge,"documents",new com.easychat.infra.mysql.repository.KnowledgeStore(mapper));TestSupport.setField(knowledge,"esClientWrapper",es);
        TestSupport.setField(knowledge,"locks",new DocumentOperationLock());TestSupport.setField(knowledge,"index","kb");doc.setFilePath(null);
        doThrow(new IllegalStateException("offline")).doNothing().when(es).deleteByQuery("kb","doc_id","d");
        assertThrows(IllegalStateException.class,()->knowledge.delete("d"));assertEquals("DELETE_FAILED",doc.getStatus());verify(mapper,never()).deleteById(anyLong());
        assertThrows(IllegalArgumentException.class,()->knowledge.retry("d"));knowledge.delete("d");verify(mapper).deleteById(1L);
    }
    @Test void hybridFallbackIsVisibleButKeywordFailurePropagates() {
        var retriever=new HybridEsRetriever();var embedding=mock(EmbeddingClient.class);when(embedding.embed(anyString())).thenThrow(new IllegalStateException("embedding down"));
        TestSupport.setField(retriever,"embeddingClient",embedding);TestSupport.setField(retriever,"esClientWrapper",es);TestSupport.setField(retriever,"index","kb");
        when(es.matchSearch("kb","content","q",3,"alpha","demo")).thenReturn(List.of());var result=retriever.retrieve(new RetrievalRequest("q",3,"alpha"));assertNotNull(result.warning());
        when(es.matchSearch("kb","content","q",3,"alpha","demo")).thenThrow(new IllegalStateException("es down"));
        assertThrows(IllegalStateException.class,()->retriever.retrieve(new RetrievalRequest("q",3,"alpha")));
    }
    @Test void duplicateUploadReusesExistingDocumentWithoutScheduling() {
        var knowledge=new KnowledgeService();var ingestion=mock(DocumentIngestionService.class);
        TestSupport.setField(knowledge,"documents",new com.easychat.infra.mysql.repository.KnowledgeStore(mapper));TestSupport.setField(knowledge,"locks",new DocumentOperationLock());
        TestSupport.setField(knowledge,"ingestionService",ingestion);
        assertEquals("d",knowledge.upload("same".getBytes(java.nio.charset.StandardCharsets.UTF_8),"a.txt","alpha"));
        verify(mapper,never()).insert(any(KnowledgeDocDO.class));verifyNoInteractions(ingestion);
    }
}