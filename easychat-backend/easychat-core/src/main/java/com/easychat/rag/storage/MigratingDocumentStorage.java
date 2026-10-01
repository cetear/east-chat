package com.easychat.rag.storage;

/** New uploads use object storage; existing local references remain readable and deletable. */
public final class MigratingDocumentStorage implements DocumentStorage {
    private final DocumentStorage objects;
    private final DocumentStorage local;
    public MigratingDocumentStorage(DocumentStorage objects, DocumentStorage local) {
        this.objects = objects; this.local = local;
    }
    public String save(byte[] content, String dataset, String name) { return objects.save(content, dataset, name); }
    public LocalFile open(String reference) { return source(reference).open(reference); }
    public void delete(String reference) { source(reference).delete(reference); }
    private DocumentStorage source(String reference) { return reference != null && reference.startsWith("s3://") ? objects : local; }
}
