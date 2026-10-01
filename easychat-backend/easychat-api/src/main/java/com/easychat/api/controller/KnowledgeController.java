package com.easychat.api.controller;

import com.easychat.api.dto.KnowledgeDocView;
import com.easychat.common.model.Result;
import com.easychat.rag.dataset.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "easychat.es.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeController {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "dataset", required = false) String dataset) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("file too large (max 20MB)");
        }
        String docCode = knowledgeService.upload(file.getBytes(), file.getOriginalFilename(), dataset);
        return Result.success(docCode);

    }

    @GetMapping("/docs")
    public Result<List<KnowledgeDocView>> list(@RequestParam(value = "dataset", required = false) String dataset) throws java.io.IOException {
        return Result.success(knowledgeService.list(dataset).stream().map(KnowledgeDocView::from).toList());

    }

    @GetMapping("/docs/{docCode}/status")
    public Result<KnowledgeDocView> status(@PathVariable("docCode") String docCode) {
        var doc = knowledgeService.get(docCode);
        if (doc == null) {
            throw new com.easychat.common.exception.BusinessException("404", "doc not found");
        }
        return Result.success(KnowledgeDocView.from(doc));

    }

    @PostMapping("/docs/{docCode}/retry")
    public Result<Void> retry(@PathVariable("docCode") String docCode) {
        knowledgeService.retry(docCode);
        return Result.success();

    }

    @DeleteMapping("/docs/{docCode}")
    public Result<Void> delete(@PathVariable("docCode") String docCode) {
        knowledgeService.delete(docCode);
        return Result.success();

    }
}
