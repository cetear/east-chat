package com.easychat.core.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelRoute {
    private Long id;
    private String modelCode;
    private String providerCode;
    private Integer priority;
    private Integer weight;
    private Integer timeoutMs;
    private Integer maxRetry;
    private Integer enabled;
    private Integer avgLatencyMs;
    private Double successRate;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
