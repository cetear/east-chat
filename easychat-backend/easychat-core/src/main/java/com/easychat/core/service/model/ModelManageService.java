package com.easychat.core.service.model;

import com.easychat.common.exception.BusinessException;
import com.easychat.core.domain.model.ModelDefinition;
import com.easychat.core.domain.model.ModelRoute;
import com.easychat.core.domain.model.ProviderAccount;
import com.easychat.core.port.ModelCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModelManageService {

    @Autowired
    private ModelCatalogRepository modelCatalogRepository;

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
    }

    public List<ProviderView> listProviders() {
        return modelCatalogRepository.findProviders().stream().map(ProviderView::from).toList();
    }

    public ProviderView getProvider(Long id) {
        ProviderAccount provider = modelCatalogRepository.findProviderById(id);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        return ProviderView.from(provider);
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
    }

    public void deleteProvider(String providerCode) {
        ProviderAccount provider = modelCatalogRepository.findProviderByCode(providerCode);
        if (provider == null) {
            throw new BusinessException("渠道商不存在");
        }
        modelCatalogRepository.deleteRoutesByProvider(providerCode);
        modelCatalogRepository.deleteProviderById(provider.getId());
    }

    public void addModel(ModelCommand command) {
        ModelDefinition exists = modelCatalogRepository.findModelByCode(command.getModelCode());
        if (exists != null) {
            throw new BusinessException("模型已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ModelDefinition model = new ModelDefinition();
        model.setModelCode(command.getModelCode());
        model.setMaxTokens(command.getMaxTokens());
        model.setDefaultConfig(command.getDefaultConfig());
        model.setEnabled(command.getEnabled() != null ? command.getEnabled() : 1);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        modelCatalogRepository.insertModel(model);
    }

    public void updateModel(ModelCommand command) {
        ModelDefinition model = modelCatalogRepository.findModelByCode(command.getModelCode());
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        if (command.getMaxTokens() != null) {
            model.setMaxTokens(command.getMaxTokens());
        }
        if (command.getDefaultConfig() != null) {
            model.setDefaultConfig(command.getDefaultConfig());
        }
        if (command.getEnabled() != null) {
            model.setEnabled(command.getEnabled());
        }
        model.setUpdatedAt(LocalDateTime.now());
        modelCatalogRepository.updateModel(model);
    }

    public void deleteModel(String modelCode) {
        ModelDefinition model = modelCatalogRepository.findModelByCode(modelCode);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        modelCatalogRepository.deleteRoutesByModel(modelCode);
        modelCatalogRepository.deleteModelById(model.getId());
    }

    public void addModelToProvider(String providerCode, ModelProviderCommand command) {
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
    }

    public List<ModelProviderView> listModelsByProvider(String providerCode) {
        return modelCatalogRepository.findRoutesByProvider(providerCode)
                .stream()
                .map(ModelProviderView::from)
                .toList();
    }

    public void deleteModelProvider(String providerCode, String modelCode) {
        modelCatalogRepository.deleteRoute(modelCode, providerCode);
    }
}
