package com.easychat.rag.pipeline.chunk;

import com.easychat.rag.pipeline.model.Chunk;
import com.easychat.rag.pipeline.model.TextBlock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 近似分块：目标长度 size（token 近似为字符数），优先段落边界，重叠 overlap 字符。
 * 暂不引入 tokenizer。
 */
@Component
public class TextChunker {

    @Value("${easychat.rag.chunk.size:512}")
    private int size;

    @Value("${easychat.rag.chunk.overlap:80}")
    private int overlap;

    public List<Chunk> chunk(List<TextBlock> blocks) {
        if (size < 1 || overlap < 0 || overlap >= size) throw new IllegalArgumentException("Invalid chunk size/overlap");
        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (TextBlock block : blocks) {
            chunks.addAll(chunkBlock(block, chunkIndex));
            chunkIndex = chunks.size();
        }
        return chunks;
    }

    private List<Chunk> chunkBlock(TextBlock block, int startIndex) {
        List<Chunk> result = new ArrayList<>();
        String text = block.getText() == null ? "" : block.getText();
        if (text.isBlank()) {
            return result;
        }
        int idx = startIndex;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                end = nearestParagraphBoundary(text, start, end);
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                result.add(new Chunk(piece, block.getPageNo(), block.getHeadingPath(), idx++));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return result;
    }

    /**
     * 在 [start, end] 区间内向前寻找最近的换行边界，避免截断段落。
     */
    private int nearestParagraphBoundary(String text, int start, int end) {
        int bound = text.lastIndexOf('\n', end);
        if (bound > start + size / 2) {
            return bound + 1;
        }
        return end;
    }
}
