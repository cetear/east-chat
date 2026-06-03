package com.easychat.core.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelDefinition {
    private Long id;
    private String modelCode;
    private Integer maxTokens;
    private String defaultConfig;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
