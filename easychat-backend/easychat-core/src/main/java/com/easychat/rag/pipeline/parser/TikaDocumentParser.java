package com.easychat.rag.pipeline.parser;

import com.easychat.rag.pipeline.model.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 按文件类型分发解析：
 * <ul>
 *   <li>pdf：PDFBox 逐页提取，保留 page_no</li>
 *   <li>md：按 # 标题切块，保留 heading_path</li>
 *   <li>docx/doc/txt 等：Tika 整篇提取</li>
 * </ul>
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    @org.springframework.beans.factory.annotation.Autowired private com.easychat.media.MediaTranscriber transcriber;
    private final Tika tika = new Tika();
    @org.springframework.beans.factory.annotation.Autowired private HttpOcrService ocr;

    @Override
    public List<TextBlock> parse(Path file, String fileType) {
        String type = fileType == null ? "" : fileType.toLowerCase();
        return switch (type) {
            case "mp3","wav","m4a","ogg","flac","mp4","webm","mov","mkv" -> parseMedia(file);
            case "pdf" -> parsePdf(file);
            case "md" -> parseMarkdown(file);
            case "png", "jpg", "jpeg", "webp" -> parseImage(file);
            default -> parseWithTika(file);
        };
    }

    private List<TextBlock> parseMedia(Path file) {
        try{return List.of(new TextBlock(transcriber.transcribe(Files.readAllBytes(file),file.getFileName().toString()),null,null));}
        catch(java.io.IOException e){throw new IllegalStateException("Media read failed",e);}
    }
    private List<TextBlock> parseImage(Path file) {
        if(ocr==null || !ocr.configured()) throw new IllegalStateException("OCR service is not configured");
        try {
            var image=javax.imageio.ImageIO.read(file.toFile());
            if(image==null) throw new IllegalArgumentException("Unsupported image encoding");
            var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
            return List.of(new TextBlock(ocr.recognize(bytes.toByteArray()),1,null));
        } catch(java.io.IOException e) {throw new IllegalStateException("Image parsing failed",e);}
    }
    private List<TextBlock> parsePdf(Path file) {
        List<TextBlock> blocks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = document.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                if((text==null || text.isBlank()) && ocr!=null && ocr.configured()) {
                    var page=document.getPage(i-1).getMediaBox();
                    float dpi=(float)Math.min(144,72*Math.sqrt(4_000_000.0/(page.getWidth()*page.getHeight())));
                    var image=new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(i-1,dpi);
                    var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
                    text=ocr.recognize(bytes.toByteArray());
                }
                if (text != null && !text.isBlank()) {
                    blocks.add(new TextBlock(text.trim(), i, null));
                }
            }
        } catch (IOException e) {
            log.error("PDF parse failed: {}", file, e);
            throw new RuntimeException("PDF parse failed", e);
        }
        return blocks;
    }

    private List<TextBlock> parseMarkdown(Path file) {
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String headingPath = null;
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    if (current.length() > 0) {
                        blocks.add(new TextBlock(current.toString(), null, headingPath));
                        current = new StringBuilder();
                    }
                    headingPath = trimmed.replaceAll("^#+\\s*", "");
                    continue;
                }
                current.append(line).append('\n');
            }
            if (current.length() > 0) {
                blocks.add(new TextBlock(current.toString(), null, headingPath));
            }
        } catch (IOException e) {
            log.error("Markdown parse failed: {}", file, e);
            throw new RuntimeException("Markdown parse failed", e);
        }
        return blocks;
    }

    private List<TextBlock> parseWithTika(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            String text = tika.parseToString(in);
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return List.of(new TextBlock(text.trim(), null, null));
        } catch (Exception e) {
            log.error("Tika parse failed: {}", file, e);
            throw new RuntimeException("Document parse failed", e);
        }
    }
}
