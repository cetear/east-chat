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

    public static ProviderView from(ProviderAccount entity) {
        if (entity == null) {
            return null;
        }
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
        return view;
    }
}
