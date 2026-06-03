package com.easychat.core.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderAccount {
    private Long id;
    private String providerCode;
    private String baseUrl;
    private String apiKey;
    private Integer enabled;
    private Integer failCount;
    private LocalDateTime lastFailTime;
    private String circuitStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
