package com.easychat.core.service.model;

import com.easychat.core.domain.model.ProviderAccount;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderView {
    private Long id;
    private String providerCode;
    private String baseUrl;
    private Integer enabled;
    private Integer failCount;
    private LocalDateTime lastFailTime;
    private String circuitStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean apiKeyConfigured;
    private String apiKeyMasked;

    public static ProviderView from(ProviderAccount entity) {
        ProviderView view = new ProviderView();
        view.setId(entity.getId());
        view.setProviderCode(entity.getProviderCode());
        view.setBaseUrl(entity.getBaseUrl());
        view.setEnabled(entity.getEnabled());
        view.setFailCount(entity.getFailCount());
        view.setLastFailTime(entity.getLastFailTime());
        view.setCircuitStatus(entity.getCircuitStatus());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        view.setApiKeyConfigured(entity.getApiKey() != null && !entity.getApiKey().isBlank());
        view.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        return view;
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
