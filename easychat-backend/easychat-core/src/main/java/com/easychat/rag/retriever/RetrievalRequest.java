package com.easychat.rag.retriever;

import com.easychat.common.util.DatasetId;

/** Single-user dataset selection; not a substitute for user authorization. */
public record RetrievalRequest(String query, int topK, String dataset, String ownerId) {
    public RetrievalRequest(String query,int topK,String dataset) {this(query,topK,dataset,"demo");}
    public RetrievalRequest {
        dataset = DatasetId.normalize(dataset); if(ownerId==null) ownerId="demo";
        if (topK < 1 || topK > 50) throw new IllegalArgumentException("topK must be 1-50");
    }
}
