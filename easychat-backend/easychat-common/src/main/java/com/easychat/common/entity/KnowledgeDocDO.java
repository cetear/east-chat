package com.easychat.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("knowledge_doc")
@Data
public class KnowledgeDocDO {
    private String ownerId;


    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_code")
    private String docCode;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_path")
    private String filePath;

    @TableField("file_hash")
    private String fileHash;

    @TableField("dataset")
    private String dataset;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("status")
    private String status;
    private String indexVersion;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
