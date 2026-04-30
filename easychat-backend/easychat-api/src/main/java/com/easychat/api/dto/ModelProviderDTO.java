package com.easychat.api.dto;

import lombok.Data;

@Data
public class ModelProviderDTO {

    private String modelCode;
    private String providerCode;
    private Integer priority;
    private Integer weight;
    private Integer timeoutMs;
    private Integer maxRetry;
    private Integer enabled;
}
