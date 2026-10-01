package com.easychat.api.dto;

import com.easychat.common.entity.KnowledgeDocDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocView {
    private String docCode;
    private String fileName;
    private String fileType;
    private String dataset;
    private Integer chunkCount;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;

    public static KnowledgeDocView from(KnowledgeDocDO doc) {
        KnowledgeDocView view = new KnowledgeDocView();
        view.setDocCode(doc.getDocCode());
        view.setFileName(doc.getFileName());
        view.setFileType(doc.getFileType());
        view.setDataset(doc.getDataset());
        view.setChunkCount(doc.getChunkCount());
        view.setStatus(doc.getStatus());
        view.setErrorMsg(doc.getErrorMsg());
        view.setCreatedAt(doc.getCreatedAt());
        return view;
    }
}
