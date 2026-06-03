package com.easychat.core.service.model;

import com.easychat.core.domain.model.ModelRoute;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelProviderView {
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

    public static ModelProviderView from(ModelRoute entity) {
        ModelProviderView view = new ModelProviderView();
        view.setId(entity.getId());
        view.setModelCode(entity.getModelCode());
        view.setProviderCode(entity.getProviderCode());
        view.setPriority(entity.getPriority());
        view.setWeight(entity.getWeight());
        view.setTimeoutMs(entity.getTimeoutMs());
        view.setMaxRetry(entity.getMaxRetry());
        view.setEnabled(entity.getEnabled());
        view.setAvgLatencyMs(entity.getAvgLatencyMs());
        view.setSuccessRate(entity.getSuccessRate());
        view.setLastUsedTime(entity.getLastUsedTime());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }
}
