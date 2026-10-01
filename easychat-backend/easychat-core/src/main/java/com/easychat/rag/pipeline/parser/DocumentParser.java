package com.easychat.rag.pipeline.parser;

import com.easychat.rag.pipeline.model.TextBlock;

import java.nio.file.Path;
import java.util.List;

public interface DocumentParser {

    /**
     * 解析文件为带来源信息的文本块。
     */
    List<TextBlock> parse(Path file, String fileType);
}
