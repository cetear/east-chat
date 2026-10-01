package com.easychat.rag.storage;
import java.nio.file.*;
import com.easychat.common.util.DatasetId;
public class LocalDocumentStorage implements DocumentStorage {
    private final Path root;
    public LocalDocumentStorage(String root) {this.root=Path.of(root).toAbsolutePath().normalize();}
    public String save(byte[] content,String dataset,String name) {
        if(!name.matches("[A-Za-z0-9-]+\\.[a-z0-9]{1,20}"))throw new IllegalArgumentException("Invalid storage name");
        try {
            Files.createDirectories(root);Path realRoot=root.toRealPath();Path dir=realRoot.resolve(DatasetId.normalize(dataset));Files.createDirectories(dir);
            Path realDir=dir.toRealPath();if(!realDir.startsWith(realRoot))throw new IllegalArgumentException("Storage directory escapes root");
            Path target=realDir.resolve(name);
            Files.write(target,content,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);return target.toString();
        }catch(java.io.IOException e){throw new IllegalStateException("Document storage failed",e);}
    }
    public LocalFile open(String reference) {
        try {
            Path file=Path.of(reference).toRealPath();if(!file.startsWith(root.toRealPath()))throw new IllegalArgumentException("Document outside storage root");
            return new LocalFile(file,false);
        }catch(java.io.IOException e){throw new IllegalStateException("Document unavailable",e);}
    }
    public void delete(String reference) {
        try {Path path=Path.of(reference);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return;Files.delete(open(reference).path());}
        catch(java.io.IOException e){throw new IllegalStateException("Document deletion failed",e);}
    }
}