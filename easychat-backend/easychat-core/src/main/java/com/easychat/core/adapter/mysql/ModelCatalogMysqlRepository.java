package com.easychat.core.adapter.mysql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easychat.core.domain.model.ModelDefinition;
import com.easychat.core.domain.model.ModelRoute;
import com.easychat.core.domain.model.ProviderAccount;
import com.easychat.core.port.ModelCatalogRepository;
import com.easychat.infra.mysql.entity.ModelDO;
import com.easychat.infra.mysql.entity.ModelProviderDO;
import com.easychat.infra.mysql.entity.ProviderDO;
import com.easychat.infra.mysql.mapper.ModelMapper;
import com.easychat.infra.mysql.mapper.ModelProviderMapper;
import com.easychat.infra.mysql.mapper.ProviderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ModelCatalogMysqlRepository implements ModelCatalogRepository {

    @Autowired
    private ProviderMapper providerMapper;

    @Autowired
    private ModelProviderMapper modelProviderMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public ProviderAccount findProviderByCode(String providerCode) {
        return toDomain(providerMapper.selectOne(
                new LambdaQueryWrapper<ProviderDO>().eq(ProviderDO::getProviderCode, providerCode)));
    }

    @Override
    public ProviderAccount findProviderById(Long id) {
        return toDomain(providerMapper.selectById(id));
    }

    @Override
    public List<ProviderAccount> findProviders() {
        return providerMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void insertProvider(ProviderAccount provider) {
        providerMapper.insert(toEntity(provider));
    }

    @Override
    public void updateProvider(ProviderAccount provider) {
        providerMapper.updateById(toEntity(provider));
    }

    @Override
    public void deleteProviderById(Long id) {
        providerMapper.deleteById(id);
    }

    @Override
    public ModelDefinition findModelByCode(String modelCode) {
        return toDomain(modelMapper.selectOne(
                new LambdaQueryWrapper<ModelDO>().eq(ModelDO::getModelCode, modelCode)));
    }

    @Override
    public ModelDefinition findModelById(Long id) {
        return toDomain(modelMapper.selectById(id));
    }

    @Override
    public List<ModelDefinition> findModels() {
        return modelMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void insertModel(ModelDefinition model) {
        modelMapper.insert(toEntity(model));
    }

    @Override
    public void updateModel(ModelDefinition model) {
        modelMapper.updateById(toEntity(model));
    }

    @Override
    public void deleteModelById(Long id) {
        modelMapper.deleteById(id);
    }

    @Override
    public ModelRoute findRoute(String modelCode, String providerCode) {
        return toDomain(modelProviderMapper.selectOne(
                new LambdaQueryWrapper<ModelProviderDO>()
                        .eq(ModelProviderDO::getModelCode, modelCode)
                        .eq(ModelProviderDO::getProviderCode, providerCode)));
    }

    @Override
    public List<ModelRoute> findRoutesByProvider(String providerCode) {
        return modelProviderMapper.selectList(
                        new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getProviderCode, providerCode))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ModelRoute> findRoutesByModel(String modelCode) {
        return modelProviderMapper.selectList(
                        new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getModelCode, modelCode))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void insertRoute(ModelRoute route) {
        modelProviderMapper.insert(toEntity(route));
    }

    @Override
    public void deleteRoutesByProvider(String providerCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getProviderCode, providerCode));
    }

    @Override
    public void deleteRoutesByModel(String modelCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getModelCode, modelCode));
    }

    @Override
    public void deleteRoute(String modelCode, String providerCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelProviderDO>()
                        .eq(ModelProviderDO::getModelCode, modelCode)
                        .eq(ModelProviderDO::getProviderCode, providerCode));
    }

    private ProviderAccount toDomain(ProviderDO entity) {
        if (entity == null) {
            return null;
        }
        ProviderAccount provider = new ProviderAccount();
        provider.setId(entity.getId());
        provider.setProviderCode(entity.getProviderCode());
        provider.setBaseUrl(entity.getBaseUrl());
        provider.setApiKey(entity.getApiKey());
        provider.setEnabled(entity.getEnabled());
        provider.setFailCount(entity.getFailCount());
        provider.setLastFailTime(entity.getLastFailTime());
        provider.setCircuitStatus(entity.getCircuitStatus());
        provider.setCreatedAt(entity.getCreatedAt());
        provider.setUpdatedAt(entity.getUpdatedAt());
        return provider;
    }

    private ProviderDO toEntity(ProviderAccount provider) {
        ProviderDO entity = new ProviderDO();
        entity.setId(provider.getId());
        entity.setProviderCode(provider.getProviderCode());
        entity.setBaseUrl(provider.getBaseUrl());
        entity.setApiKey(provider.getApiKey());
        entity.setEnabled(provider.getEnabled());
        entity.setFailCount(provider.getFailCount());
        entity.setLastFailTime(provider.getLastFailTime());
        entity.setCircuitStatus(provider.getCircuitStatus());
        entity.setCreatedAt(provider.getCreatedAt());
        entity.setUpdatedAt(provider.getUpdatedAt());
        return entity;
    }

    private ModelDefinition toDomain(ModelDO entity) {
        if (entity == null) {
            return null;
        }
        ModelDefinition model = new ModelDefinition();
        model.setId(entity.getId());
        model.setModelCode(entity.getModelCode());
        model.setModelName(entity.getModelName());
        model.setModelType(entity.getModelType());
        model.setModelFamily(entity.getModelFamily());
        model.setContextWindow(entity.getContextWindow());
        model.setMaxOutputTokens(entity.getMaxOutputTokens());
        model.setDefaultTemperature(entity.getDefaultTemperature());
        model.setDefaultTopP(entity.getDefaultTopP());
        model.setDefaultConfig(entity.getDefaultConfig());
        model.setEnabled(entity.getEnabled());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    private ModelDO toEntity(ModelDefinition model) {
        ModelDO entity = new ModelDO();
        entity.setId(model.getId());
        entity.setModelCode(model.getModelCode());
        entity.setModelName(model.getModelName());
        entity.setModelType(model.getModelType());
        entity.setModelFamily(model.getModelFamily());
        entity.setContextWindow(model.getContextWindow());
        entity.setMaxOutputTokens(model.getMaxOutputTokens());
        entity.setDefaultTemperature(model.getDefaultTemperature());
        entity.setDefaultTopP(model.getDefaultTopP());
        entity.setDefaultConfig(model.getDefaultConfig());
        entity.setEnabled(model.getEnabled());
        entity.setCreatedAt(model.getCreatedAt());
        entity.setUpdatedAt(model.getUpdatedAt());
        return entity;
    }

    private ModelRoute toDomain(ModelProviderDO entity) {
        if (entity == null) {
            return null;
        }
        ModelRoute route = new ModelRoute();
        route.setId(entity.getId());
        route.setModelCode(entity.getModelCode());
        route.setProviderCode(entity.getProviderCode());
        route.setPriority(entity.getPriority());
        route.setWeight(entity.getWeight());
        route.setTimeoutMs(entity.getTimeoutMs());
        route.setMaxRetry(entity.getMaxRetry());
        route.setEnabled(entity.getEnabled());
        route.setAvgLatencyMs(entity.getAvgLatencyMs());
        route.setSuccessRate(entity.getSuccessRate());
        route.setLastUsedTime(entity.getLastUsedTime());
        route.setCreatedAt(entity.getCreatedAt());
        route.setUpdatedAt(entity.getUpdatedAt());
        return route;
    }

    private ModelProviderDO toEntity(ModelRoute route) {
        ModelProviderDO entity = new ModelProviderDO();
        entity.setId(route.getId());
        entity.setModelCode(route.getModelCode());
        entity.setProviderCode(route.getProviderCode());
        entity.setPriority(route.getPriority());
        entity.setWeight(route.getWeight());
        entity.setTimeoutMs(route.getTimeoutMs());
        entity.setMaxRetry(route.getMaxRetry());
        entity.setEnabled(route.getEnabled());
        entity.setAvgLatencyMs(route.getAvgLatencyMs());
        entity.setSuccessRate(route.getSuccessRate());
        entity.setLastUsedTime(route.getLastUsedTime());
        entity.setCreatedAt(route.getCreatedAt());
        entity.setUpdatedAt(route.getUpdatedAt());
        return entity;
    }
}
