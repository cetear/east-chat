package com.easychat.rag.storage;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class StorageConfiguration {
    @Bean public DocumentStorage documentStorage(Environment env,java.net.http.HttpClient client) {
        var local = new LocalDocumentStorage(env.getProperty("easychat.rag.storage-dir","./data/knowledge"));
        return switch(env.getProperty("easychat.rag.storage.mode","local")) {
            case "local" -> local;
            case "s3" -> new MigratingDocumentStorage(new S3DocumentStorage(env.getRequiredProperty("easychat.rag.storage.s3.endpoint"),env.getRequiredProperty("easychat.rag.storage.s3.bucket"),env.getProperty("easychat.rag.storage.s3.region","us-east-1"),env.getRequiredProperty("easychat.rag.storage.s3.access-key"),env.getRequiredProperty("easychat.rag.storage.s3.secret-key"),client), local);
            default -> throw new IllegalArgumentException("Unknown document storage mode");
        };
    }
}
