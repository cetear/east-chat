package com.easychat.rag.pipeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的一段文本块，保留来源信息（页码、标题路径）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextBlock {
    private String text;
    private Integer pageNo;
    private String headingPath;
}
