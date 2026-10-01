package com.easychat.rag.pipeline;
import com.easychat.infra.es.EsClientWrapper;
import com.easychat.common.entity.KnowledgeDocDO;
import com.easychat.infra.mysql.repository.KnowledgeStore;
import com.easychat.rag.config.EmbeddingProperties;
import com.easychat.rag.embedding.EmbeddingClient;
import com.easychat.rag.pipeline.chunk.TextChunker;
import com.easychat.rag.pipeline.model.Chunk;
import com.easychat.rag.pipeline.parser.DocumentParser;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
@Slf4j @Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class DocumentIngestionService {
    @Autowired private KnowledgeStore documents;
    @Autowired private DocumentParser documentParser;
    @Autowired(required=false) private com.easychat.rag.storage.DocumentStorage storage;
    @Autowired private TextChunker textChunker;
    @Autowired(required=false) private EmbeddingClient embeddingClient;
    @Autowired private EmbeddingProperties embeddingProperties;
    @Autowired private EsClientWrapper esClientWrapper;
    @Autowired private com.easychat.infra.es.EsIndexManager indexManager;
    @Autowired private DocumentOperationLock locks;
    @Autowired @Qualifier("ingestionExecutor") private Executor executor;
    @Value("${easychat.rag.index:easychat_kb}") private String index;
    public void ingestAsync(String code) {
        try { executor.execute(() -> ingest(code)); }
        catch (java.util.concurrent.RejectedExecutionException e) { log.warn("Ingestion queue full; document remains pending: {}",code); }
    }
    public void ingest(String code) {
        try (var lease=locks.acquire(code)) {
            KnowledgeDocDO doc=find(code);
            if(doc==null) return;
            int claimed=lease.guarded(() -> documents.claim(doc.getId()));
            if(claimed!=1) return;
            try {
                indexManager.ensureIndex();
                lease.check();
                if(doc.getIndexVersion()!=null) esClientWrapper.deleteByQuery(index,"generation",code+":"+doc.getIndexVersion());
                var chunks=textChunker.chunk(parse(doc));
                if(chunks.isEmpty()) throw new IllegalArgumentException("No searchable text extracted (OCR is not supported)");
                doc.setIndexVersion(UUID.randomUUID().toString());
                int batchSize=Math.max(1,embeddingProperties.getBatchSize());
                for(int start=0;start<chunks.size();start+=batchSize) {
                    var batch=chunks.subList(start,Math.min(chunks.size(),start+batchSize));
                    List<float[]> vectors=embeddingClient==null ? List.of() : embeddingClient.embed(batch.stream().map(Chunk::getContent).toList());
                    if(embeddingClient!=null && vectors.size()!=batch.size()) throw new IllegalArgumentException("Embedding count mismatch");
                    List<Map<String,Object>> records=new ArrayList<>();
                    for(int i=0;i<batch.size();i++) {
                        float[] vector=embeddingClient==null ? null : vectors.get(i);
                        if(embeddingClient!=null && (vector==null || vector.length!=embeddingProperties.getDimensions())) throw new IllegalArgumentException("Embedding dimension mismatch");
                        records.add(record(doc,batch.get(i),vector));
                    }
                    lease.check(); esClientWrapper.bulkIndex(index,records);
                }
                doc.setChunkCount(chunks.size()); lease.guarded(()->{update(doc,com.easychat.common.constant.DocumentStatus.INDEXED,null);return null;});
            } catch(Exception e) {
                lease.guarded(()->{update(doc,com.easychat.common.constant.DocumentStatus.FAILED,Objects.toString(e.getMessage(),"Ingestion failed"));return null;});
                // Unpublished generations are excluded by retrieval even if cleanup fails.
                try { if(doc.getIndexVersion()!=null) esClientWrapper.deleteByQuery(index,"generation",code+":"+doc.getIndexVersion()); } catch(Exception cleanup) { log.warn("Partial chunks retained for retry: {}",code); }
            }
        } catch(Exception e) { log.error("Ingestion failed: {}",code,e); }
    }
    @Autowired(required=false) private com.easychat.infra.coordination.MysqlLeaseService distributed;
    public int recoverInterrupted() {
        if(distributed!=null) {
            recoverExpired(); return submitPending();
        }
        documents.recoverLocal();
        return submitPending();
    }
    @Scheduled(fixedDelay=30000) public void recoverExpired() {
        if(distributed==null) return;
        documents.recoverExpired();
    }
    @Scheduled(fixedDelayString="${easychat.rag.retry-pending-ms:30000}")
    public int submitPending() {
        var pending=documents.pending();
        pending.forEach(doc -> ingestAsync(doc.getDocCode())); return pending.size();
    }
    private java.util.List<com.easychat.rag.pipeline.model.TextBlock> parse(KnowledgeDocDO doc) throws Exception {
        if(storage==null) return documentParser.parse(Path.of(doc.getFilePath()),doc.getFileType());
        try(var file=storage.open(doc.getFilePath())) {return documentParser.parse(file.path(),doc.getFileType());}
    }
    private KnowledgeDocDO find(String code) {return documents.find(code);}
    private void update(KnowledgeDocDO doc,String status,String error) {documents.outcome(doc,status,error);}
    private Map<String,Object> record(KnowledgeDocDO doc,Chunk chunk,float[] vector) {
        Map<String,Object> record=new HashMap<>();
        String generation=doc.getDocCode()+":"+doc.getIndexVersion();
        record.put("generation",generation);
        record.put("chunk_id",generation+":"+chunk.getChunkIndex()); record.put("doc_id",doc.getDocCode());
        record.put("dataset",doc.getDataset()); record.put("title",doc.getFileName()); record.put("content",chunk.getContent());
        record.put("page_no",chunk.getPageNo()); record.put("heading_path",chunk.getHeadingPath()); record.put("chunk_index",chunk.getChunkIndex());
        record.put("token_count",chunk.getContent().length()); record.put("created_at",System.currentTimeMillis());
        if(vector!=null) { List<Float> values=new ArrayList<>(); for(float v:vector) { if(!Float.isFinite(v)) throw new IllegalArgumentException("Invalid vector"); values.add(v); } record.put("content_vector",values); }
        return record;
    }
}
