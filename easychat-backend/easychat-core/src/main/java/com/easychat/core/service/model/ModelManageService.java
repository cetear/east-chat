package com.easychat.core.service.model;

import com.easychat.common.exception.BusinessException;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
import com.easychat.common.port.ModelCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@org.springframework.transaction.annotation.Transactional
public class ModelManageService {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    @Autowired
    private ModelCatalogRepository modelCatalogRepository;
    @Autowired private com.easychat.core.router.ProviderRegistry registry;

    public void addProvider(ProviderCommand command) {
        ProviderAccount exists = modelCatalogRepository.findProviderByCode(command.getProviderCode());
        if (exists != null) {
            throw new BusinessException("渠道商已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ProviderAccount provider = new ProviderAccount();
        provider.setProviderCode(command.getProviderCode());
        provider.setBaseUrl(command.getBaseUrl());
        provider.setApiKey(command.getApiKey());
        provider.setEnabled(command.getEnabled() != null ? command.getEnabled() : 1);
        provider.setFailCount(0);
        provider.setCircuitStatus("CLOSED");
        provider.setCreatedAt(now);
        provider.setUpdatedAt(now);
        modelCatalogRepository.insertProvider(provider);
        refreshAfterCommit();
    }

    public List<ProviderAccount> listProviders() {
        return modelCatalogRepository.findProviders();
    }

    public ProviderAccount getProvider(Long id) {
        ProviderAccount provider = modelCatalogRepository.findProviderById(id);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        return provider;
    }

    public ProviderAccount getProviderByCode(String providerCode) {
        ProviderAccount provider = modelCatalogRepository.findProviderByCode(providerCode);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        return provider;
    }

    public void updateProvider(ProviderCommand command) {
        ProviderAccount provider = modelCatalogRepository.findProviderByCode(command.getProviderCode());
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        if (command.getBaseUrl() != null) {
            provider.setBaseUrl(command.getBaseUrl());
        }
        if (command.getApiKey() != null) {
            provider.setApiKey(command.getApiKey());
        }
        if (command.getEnabled() != null) {
            provider.setEnabled(command.getEnabled());
        }
        provider.setUpdatedAt(LocalDateTime.now());
        modelCatalogRepository.updateProvider(provider);
        refreshAfterCommit();
    }

    public void deleteProvider(String providerCode) {
        ProviderAccount provider = modelCatalogRepository.findProviderByCode(providerCode);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        modelCatalogRepository.deleteRoutesByProvider(providerCode);
        refreshAfterCommit();
        modelCatalogRepository.deleteProviderById(provider.getId());
        refreshAfterCommit();
    }

    public void addModel(ModelCommand command) {
        ModelDefinition exists = modelCatalogRepository.findModelByCode(command.getModelCode());
        if (exists != null) {
            throw new BusinessException("模型已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ModelDefinition model = new ModelDefinition();
        model.setModelCode(command.getModelCode());
        model.setModelName(command.getModelName());
        model.setModelType(command.getModelType() != null ? command.getModelType() : "chat");
        model.setModelFamily(command.getModelFamily());
        model.setContextWindow(command.getContextWindow());
        model.setMaxOutputTokens(command.getMaxOutputTokens());
        model.setDefaultTemperature(command.getDefaultTemperature());
        model.setDefaultTopP(command.getDefaultTopP());
        model.setDefaultConfig(normalizeJsonConfig(command.getDefaultConfig()));
        model.setSupportVision(command.getSupportVision() != null ? command.getSupportVision() : 0);
        model.setEnabled(command.getEnabled() != null ? command.getEnabled() : 1);
        validateModelBudget(model);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        modelCatalogRepository.insertModel(model);
        refreshAfterCommit();
    }

    public List<ModelDefinition> listModels() {
        return modelCatalogRepository.findModels();
    }

    public ModelDefinition getModel(Long id) {
        ModelDefinition model = modelCatalogRepository.findModelById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return model;
    }

    public ModelDefinition getModelByCode(String modelCode) {
        ModelDefinition model = modelCatalogRepository.findModelByCode(modelCode);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return model;
    }

    public void updateModel(ModelCommand command) {
        ModelDefinition model = modelCatalogRepository.findModelByCode(command.getModelCode());
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        if (command.getModelName() != null) {
            model.setModelName(command.getModelName());
        }
        if (command.getModelType() != null) {
            model.setModelType(command.getModelType());
        }
        if (command.getModelFamily() != null) {
            model.setModelFamily(command.getModelFamily());
        }
        if (command.getContextWindow() != null) {
            model.setContextWindow(command.getContextWindow());
        }
        if (command.getMaxOutputTokens() != null) {
            model.setMaxOutputTokens(command.getMaxOutputTokens());
        }
        if (command.getDefaultTemperature() != null) {
            model.setDefaultTemperature(command.getDefaultTemperature());
        }
        if (command.getDefaultTopP() != null) {
            model.setDefaultTopP(command.getDefaultTopP());
        }
        if (command.getDefaultConfig() != null) {
            model.setDefaultConfig(normalizeJsonConfig(command.getDefaultConfig()));
        }
        if (command.getSupportVision() != null) {
            model.setSupportVision(command.getSupportVision());
        }
        if (command.getEnabled() != null) {
            model.setEnabled(command.getEnabled());
        }
        model.setUpdatedAt(LocalDateTime.now());
        validateModelBudget(model);
        modelCatalogRepository.updateModel(model);
        refreshAfterCommit();
    }

    public void deleteModel(String modelCode) {
        ModelDefinition model = modelCatalogRepository.findModelByCode(modelCode);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        modelCatalogRepository.deleteRoutesByModel(modelCode);
        refreshAfterCommit();
        modelCatalogRepository.deleteModelById(model.getId());
        refreshAfterCommit();
    }

    public void addModelToProvider(String providerCode, ModelProviderCommand command) {
        validateRoute(command);
        ProviderAccount provider = modelCatalogRepository.findProviderByCode(providerCode);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }

        ModelDefinition model = modelCatalogRepository.findModelByCode(command.getModelCode());
        if (model == null) {
            throw new BusinessException("模型不存在");
        }

        ModelRoute exists = modelCatalogRepository.findRoute(command.getModelCode(), providerCode);
        if (exists != null) {
            throw new BusinessException("模型渠道配置已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ModelRoute route = new ModelRoute();
        route.setModelCode(command.getModelCode());
        route.setProviderCode(providerCode);
        route.setPriority(command.getPriority() != null ? command.getPriority() : 0);
        route.setWeight(command.getWeight() != null ? command.getWeight() : 1);
        route.setTimeoutMs(command.getTimeoutMs() != null ? command.getTimeoutMs() : 60000);
        route.setMaxRetry(command.getMaxRetry() != null ? command.getMaxRetry() : 0);
        route.setEnabled(command.getEnabled() != null ? command.getEnabled() : 1);
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        modelCatalogRepository.insertRoute(route);
        refreshAfterCommit();
    }

    public List<ModelRoute> listModelsByProvider(String providerCode) {
        return modelCatalogRepository.findRoutesByProvider(providerCode)
                ;
    }

    public void updateModelProvider(String providerCode, String modelCode, ModelProviderCommand command) {
        validateRoute(command);
        ModelRoute route = modelCatalogRepository.findRoute(modelCode, providerCode);
        if (route == null) throw new BusinessException("模型渠道配置不存在");
        if (command.getPriority() != null) route.setPriority(command.getPriority());
        if (command.getWeight() != null) route.setWeight(command.getWeight());
        if (command.getTimeoutMs() != null) route.setTimeoutMs(command.getTimeoutMs());
        if (command.getMaxRetry() != null) route.setMaxRetry(command.getMaxRetry());
        if (command.getEnabled() != null) route.setEnabled(command.getEnabled());
        route.setUpdatedAt(LocalDateTime.now());
        modelCatalogRepository.updateRoute(route);
        refreshAfterCommit();
    }

    private void validateRoute(ModelProviderCommand command) {
        if(command.getWeight()!=null && command.getWeight()<1) throw new IllegalArgumentException("weight must be positive");
        if(command.getMaxRetry()!=null && (command.getMaxRetry()<0 || command.getMaxRetry()>3)) throw new IllegalArgumentException("maxRetry must be 0-3");
        if(command.getTimeoutMs()!=null && command.getTimeoutMs()<1) throw new IllegalArgumentException("timeoutMs must be positive");
        if(command.getEnabled()!=null && command.getEnabled()!=0 && command.getEnabled()!=1) throw new IllegalArgumentException("enabled must be 0 or 1");
    }

    public List<ModelRoute> listProvidersByModel(String modelCode) {
        return modelCatalogRepository.findRoutesByModel(modelCode)
                ;
    }

    public void deleteModelProvider(String providerCode, String modelCode) {
        modelCatalogRepository.deleteRoute(modelCode, providerCode);
        refreshAfterCommit();
    }

    private void refreshAfterCommit() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    public void afterCommit() { registry.reload(); }
                });
        } else registry.reload();
    }

    private String normalizeJsonConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            var node = json.readTree(config);
            if (!node.isObject())
                throw new IllegalArgumentException("default_config must be a JSON object");
            if (node.has("model_name") && (!node.get("model_name").isTextual() || node.get("model_name").asText().isBlank()))
                throw new IllegalArgumentException("model_name must be a non-empty string");
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid default_config", e); }
        return config;
    }

    private void validateModelBudget(ModelDefinition model) {
        int output = model.getMaxOutputTokens() == null ? 2000 : model.getMaxOutputTokens();
        if (output < 1) throw new IllegalArgumentException("maxOutputTokens must be positive");
        if (model.getContextWindow() != null && (long) model.getContextWindow() - output < 128)
            throw new IllegalArgumentException("contextWindow must exceed maxOutputTokens by at least 128");
    }
}
