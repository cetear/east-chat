package com.easychat.rag.pipeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 入库前的最小知识单元（分块）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Chunk {
    private String content;
    private Integer pageNo;
    private String headingPath;
    private int chunkIndex;
}
