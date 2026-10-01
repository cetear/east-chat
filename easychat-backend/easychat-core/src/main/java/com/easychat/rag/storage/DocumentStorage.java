package com.easychat.rag.storage;
import java.nio.file.*;
public interface DocumentStorage {
    String save(byte[] content,String dataset,String name);
    LocalFile open(String reference);
    void delete(String reference);
    record LocalFile(Path path,boolean temporary) implements AutoCloseable {
        public void close() throws java.io.IOException {if(temporary)Files.deleteIfExists(path);}
    }
}