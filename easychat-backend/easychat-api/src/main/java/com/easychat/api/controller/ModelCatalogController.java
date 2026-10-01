package com.easychat.api.controller;

import com.easychat.common.model.Result;
import com.easychat.core.service.model.ModelManageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated users may choose models without access to provider administration.
 */
@RestController
public class ModelCatalogController {
    private final ModelManageService models;

    public ModelCatalogController(ModelManageService models) {
        this.models = models;
    }

    public record Entry(String modelCode, String modelName, Integer supportVision) {
    }

    @GetMapping("/api/models")
    public Result<List<Entry>> list() {
        return Result.success(models.listModels().stream().filter(m -> Integer.valueOf(1).equals(m.getEnabled()))
                .map(m -> new Entry(m.getModelCode(), m.getModelName(), m.getSupportVision())).toList());
    }
}
