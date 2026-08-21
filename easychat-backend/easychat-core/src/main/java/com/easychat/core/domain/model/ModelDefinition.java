package com.easychat.core.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ModelDefinition {
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
}
