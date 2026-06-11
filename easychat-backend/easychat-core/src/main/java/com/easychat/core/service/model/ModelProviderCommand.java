package com.easychat.core.service.model;

import lombok.Data;

@Data
public class ModelProviderCommand {
    private String modelCode;
    private String providerCode;
    private Integer priority;
    private Integer weight;
    private Integer timeoutMs;
    private Integer maxRetry;
    private Integer enabled;
}
