package com.easychat.rag.dataset;

import com.easychat.infra.es.EsClientWrapper;
import com.easychat.common.util.DatasetId;
import com.easychat.common.entity.KnowledgeDocDO;
import com.easychat.infra.mysql.repository.KnowledgeStore;
import com.easychat.rag.pipeline.DocumentIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 知识库文档管理：上传、查询、重试、删除。
 */
@Slf4j
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class KnowledgeService {

    @Autowired
    private KnowledgeStore documents;

    @Autowired
    private DocumentIngestionService ingestionService;
    @Autowired private com.easychat.rag.pipeline.DocumentOperationLock locks;

    @Autowired
    private EsClientWrapper esClientWrapper;

    @Value("${easychat.rag.storage-dir:./data/knowledge}")
    private String storageDir;
    @Autowired(required=false) private com.easychat.rag.storage.DocumentStorage storage;
    private com.easychat.rag.storage.DocumentStorage storage() {return storage==null?new com.easychat.rag.storage.LocalDocumentStorage(storageDir):storage;}

    @Value("${easychat.rag.index:easychat_kb}")
    private String index;

    public String upload(byte[] content, String originalFilename, String dataset) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("file content is empty");
        if (content.length > 20 * 1024 * 1024) throw new IllegalArgumentException("file too large (max 20MB)");
        String ds = DatasetId.normalize(dataset);
        resolveFileType(originalFilename == null ? "unknown" : originalFilename);
        String hash = sha256(content);
        try (var uploadLease = locks.acquire("upload:" + com.easychat.common.security.ActorContext.current().userId() + ":" + ds + ":" + hash)) {
            KnowledgeDocDO existing = documents.duplicate(ds,com.easychat.common.security.ActorContext.current().userId(),hash);
            if (existing != null) {
                if(existing.getStatus().startsWith("DELET")) throw new com.easychat.common.exception.BusinessException("409","Document deletion must finish before upload");
                return existing.getDocCode();
            }
            return uploadNew(content, originalFilename, ds, uploadLease);
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String uploadNew(byte[] content, String originalFilename, String dataset, com.easychat.rag.pipeline.DocumentOperationLock.Guard uploadLease) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content is empty");
        }
        if (content.length > 20 * 1024 * 1024) throw new IllegalArgumentException("file too large (max 20MB)");
        String fileName = originalFilename != null ? originalFilename : "unknown";
        String fileType = resolveFileType(fileName);
        String docCode = UUID.randomUUID().toString().replace("-", "");
        String ds = DatasetId.normalize(dataset);
        String target = storage().save(content, ds, docCode + "." + fileType);

        KnowledgeDocDO doc = new KnowledgeDocDO();
        doc.setDocCode(docCode); doc.setOwnerId(com.easychat.common.security.ActorContext.current().userId());
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFilePath(target);
        doc.setFileHash(sha256(content));
        doc.setDataset(ds);
        doc.setChunkCount(0);
        doc.setStatus(com.easychat.common.constant.DocumentStatus.PENDING);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        try { uploadLease.guarded(() -> documents.insert(doc)); }
        catch (RuntimeException e) {
            try { storage().delete(target); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }

        ingestionService.ingestAsync(docCode);
        return docCode;
    }

    public List<KnowledgeDocDO> list(String dataset) {
        return documents.list(com.easychat.common.security.ActorContext.current().userId(),dataset);
    }
    public KnowledgeDocDO get(String docCode) {return documents.owned(docCode,com.easychat.common.security.ActorContext.current().userId());}

    public void retry(String docCode) {
        try (var lease = locks.acquire(docCode)) {
        KnowledgeDocDO doc = get(docCode);
        if (doc == null) {
            throw new IllegalArgumentException("doc not found: " + docCode);
        }
        if (com.easychat.common.constant.DocumentStatus.DELETING.equals(doc.getStatus()) || com.easychat.common.constant.DocumentStatus.DELETE_FAILED.equals(doc.getStatus()))
            throw new IllegalArgumentException("Retry deletion using DELETE before uploading again");
        if (com.easychat.common.constant.DocumentStatus.PENDING.equals(doc.getStatus()) || com.easychat.common.constant.DocumentStatus.INDEXING.equals(doc.getStatus())) return;
        doc.setStatus(com.easychat.common.constant.DocumentStatus.PENDING);
        doc.setErrorMsg(null);
        doc.setUpdatedAt(LocalDateTime.now());
        lease.guarded(() -> documents.update(doc));
        ingestionService.ingestAsync(docCode);
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void delete(String docCode) {
        try (var lease = locks.acquire(docCode)) {
        KnowledgeDocDO doc = get(docCode);
        if (doc == null) {
            return;
        }
        doc.setStatus(com.easychat.common.constant.DocumentStatus.DELETING);
        lease.guarded(() -> documents.update(doc));
        try {
            lease.check(); esClientWrapper.deleteByQuery(index, "doc_id", docCode);
            if (doc.getFilePath() != null) {
                storage().delete(doc.getFilePath());
            }
        } catch (Exception e) {
            doc.setStatus(com.easychat.common.constant.DocumentStatus.DELETE_FAILED);
            doc.setErrorMsg("Deletion failed; retry DELETE");
            lease.guarded(() -> documents.update(doc));
            throw new IllegalStateException("Deletion failed; metadata retained for retry", e);
        }
        lease.guarded(() -> documents.delete(doc.getId()));
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String resolveFileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "txt";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,20}")) {
            throw new IllegalArgumentException("invalid file extension");
        }
        return extension;
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
