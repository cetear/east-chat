package com.easychat.rag.retriever;
import java.util.List;
public record RetrievalResult(List<Retriever.Document> documents,String warning) {}
