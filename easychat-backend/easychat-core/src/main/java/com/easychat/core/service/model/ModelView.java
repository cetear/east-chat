package com.easychat.core.service.model;

import com.easychat.core.domain.model.ModelDefinition;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelView {
    private Long id;
    private String modelCode;
    private Integer maxTokens;
    private String defaultConfig;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelView from(ModelDefinition entity) {
        ModelView view = new ModelView();
        view.setId(entity.getId());
        view.setModelCode(entity.getModelCode());
        view.setMaxTokens(entity.getMaxTokens());
        view.setDefaultConfig(entity.getDefaultConfig());
        view.setEnabled(entity.getEnabled());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }
}
