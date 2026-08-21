package com.easychat.core.service.model;

import com.easychat.core.domain.model.ModelDefinition;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ModelView {
    private Long id;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String modelFamily;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private BigDecimal defaultTemperature;
    private BigDecimal defaultTopP;
    private String defaultConfig;
    private Integer supportVision;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelView from(ModelDefinition entity) {
        if (entity == null) {
            return null;
        }
        ModelView view = new ModelView();
        view.setId(entity.getId());
        view.setModelCode(entity.getModelCode());
        view.setModelName(entity.getModelName());
        view.setModelType(entity.getModelType());
        view.setModelFamily(entity.getModelFamily());
        view.setContextWindow(entity.getContextWindow());
        view.setMaxOutputTokens(entity.getMaxOutputTokens());
        view.setDefaultTemperature(entity.getDefaultTemperature());
        view.setDefaultTopP(entity.getDefaultTopP());
        view.setDefaultConfig(entity.getDefaultConfig());
        view.setSupportVision(entity.getSupportVision());
        view.setEnabled(entity.getEnabled());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }
}
