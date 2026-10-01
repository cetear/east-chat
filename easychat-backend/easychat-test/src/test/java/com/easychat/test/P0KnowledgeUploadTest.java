package com.easychat.test;

import com.easychat.common.entity.KnowledgeDocDO;
import com.easychat.infra.mysql.mapper.KnowledgeDocMapper;
import com.easychat.rag.dataset.KnowledgeService;
import com.easychat.rag.pipeline.DocumentIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class P0KnowledgeUploadTest {
    @TempDir Path temp;
    private KnowledgeService service;
    private KnowledgeDocMapper mapper;
    private DocumentIngestionService ingestion;
    private Path root;

    @BeforeEach
    void setUp() {
        root = temp.resolve("storage");
        service = new KnowledgeService(); TestSupport.setField(service, "locks", new com.easychat.rag.pipeline.DocumentOperationLock());
        mapper = mock(KnowledgeDocMapper.class);
        ingestion = mock(DocumentIngestionService.class);
        TestSupport.setField(service, "storageDir", root.toString());
        TestSupport.setField(service, "documents", new com.easychat.infra.mysql.repository.KnowledgeStore(mapper));
        TestSupport.setField(service, "ingestionService", ingestion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside", "..\\outside", "/tmp/outside", "C:\\outside",
            "\\\\server\\share", "a/b", "a\\b", "a:b", "a.", "CON", "lpt1", "a?b", "*",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsUnsafeDatasetBeforeAnySideEffect(String dataset) {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(new byte[]{1}, "notes.txt", dataset));
        assertFalse(Files.exists(root));
        verifyNoInteractions(mapper, ingestion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file.txt/../../outside", "file.txt\\..\\outside", "file.a:b", "file.exe ",
            "file.abcdefghijklmnopqrstu"})
    void rejectsUnsafeExtensionBeforeAnySideEffect(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(new byte[]{1}, name, "docs"));
        assertFalse(Files.exists(root));
        verifyNoInteractions(mapper, ingestion);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "team-A_1"})
    void writesOnlyInsideSelectedDatasetAndSchedulesIngestion(String dataset) throws IOException {
        byte[] content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String code = service.upload(content, "notes.TXT", dataset);
        ArgumentCaptor<KnowledgeDocDO> saved = ArgumentCaptor.forClass(KnowledgeDocDO.class);
        verify(mapper).insert(saved.capture());
        KnowledgeDocDO doc = saved.getValue();
        String expectedDataset = dataset == null || dataset.isBlank() ? "default" : dataset;
        Path file = Path.of(doc.getFilePath());
        assertEquals(expectedDataset, doc.getDataset());
        assertEquals(root.toRealPath().resolve(expectedDataset), file.getParent());
        assertEquals(code + ".txt", file.getFileName().toString());
        assertArrayEquals(content, Files.readAllBytes(file));
        verify(ingestion).ingestAsync(code);
    }

    @Test
    void rejectsDatasetDirectoryLinkOutsideRoot() throws IOException {
        Files.createDirectories(root);
        Path outside = Files.createDirectory(temp.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("linked"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "Host cannot create a symbolic link: " + e.getMessage());
        }
        assertThrows(RuntimeException.class,
                () -> service.upload(new byte[]{1}, "notes.txt", "linked"));
        try (var entries = Files.list(outside)) {
            assertEquals(0, entries.count());
        }
        verifyNoInteractions(mapper, ingestion);
    }
}
