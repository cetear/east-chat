package com.easychat.api.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.easychat.api.dto.EsDocumentRequest;
import com.easychat.common.model.Result;
import com.easychat.infra.es.EsClientWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/es")
public class EsController {

    @Autowired
    private ElasticsearchClient elasticsearchClient;  // Spring Boot 自动注入

    @Autowired
    private EsClientWrapper esClientWrapper;

    @GetMapping("/test")
    public String testEs() throws Exception {
        var info = elasticsearchClient.info();
        return "连接成功: " + info.clusterName();
    }

    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> getAllDocuments(
            @RequestParam(value = "index", defaultValue = "easychat") String index,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        try {
            return Result.success(esClientWrapper.searchAll(index, size));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/document")
    public Result<String> indexDocument(@RequestBody EsDocumentRequest request) {
        if (request == null || request.getIndex() == null || request.getIndex().isBlank()) {
            return Result.error(400, "index is required");
        }
        if (request.getDocument() == null || request.getDocument().isEmpty()) {
            return Result.error(400, "document is required");
        }
        try {
            String result = esClientWrapper.indexDocument(
                request.getIndex(), request.getId(), request.getDocument());
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

}
