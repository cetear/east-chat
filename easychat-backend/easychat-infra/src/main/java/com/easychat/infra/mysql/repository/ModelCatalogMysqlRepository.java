package com.easychat.infra.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
import com.easychat.common.port.ModelCatalogRepository;
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
        return providerMapper.selectOne(
                new LambdaQueryWrapper<ProviderAccount>().eq(ProviderAccount::getProviderCode, providerCode));
    }

    @Override
    public ProviderAccount findProviderById(Long id) {
        return providerMapper.selectById(id);
    }

    @Override
    public List<ProviderAccount> findProviders() {
        return providerMapper.selectList(null);
    }

    @Override
    public void insertProvider(ProviderAccount provider) {
        providerMapper.insert(provider);
    }

    @Override
    public void updateProvider(ProviderAccount provider) {
        if (provider.getId() == null) {
            throw new BusinessException("Provider id is required for update");
        }
        providerMapper.updateById(provider);
    }

    @Override
    public void deleteProviderById(Long id) {
        providerMapper.deleteById(id);
    }

    @Override
    public ModelDefinition findModelByCode(String modelCode) {
        return modelMapper.selectOne(
                new LambdaQueryWrapper<ModelDefinition>().eq(ModelDefinition::getModelCode, modelCode));
    }

    @Override
    public ModelDefinition findModelById(Long id) {
        return modelMapper.selectById(id);
    }

    @Override
    public List<ModelDefinition> findModels() {
        return modelMapper.selectList(null);
    }

    @Override
    public void insertModel(ModelDefinition model) {
        modelMapper.insert(model);
    }

    @Override
    public void updateModel(ModelDefinition model) {
        if (model.getId() == null) {
            throw new BusinessException("Model id is required for update");
        }
        modelMapper.updateById(model);
    }

    @Override
    public void deleteModelById(Long id) {
        modelMapper.deleteById(id);
    }

    @Override
    public ModelRoute findRoute(String modelCode, String providerCode) {
        return modelProviderMapper.selectOne(
                new LambdaQueryWrapper<ModelRoute>()
                        .eq(ModelRoute::getModelCode, modelCode)
                        .eq(ModelRoute::getProviderCode, providerCode));
    }

    @Override
    public List<ModelRoute> findRoutesByProvider(String providerCode) {
        return modelProviderMapper.selectList(
                        new LambdaQueryWrapper<ModelRoute>().eq(ModelRoute::getProviderCode, providerCode))
                ;
    }

    @Override
    public List<ModelRoute> findRoutesByModel(String modelCode) {
        return modelProviderMapper.selectList(
                        new LambdaQueryWrapper<ModelRoute>().eq(ModelRoute::getModelCode, modelCode))
                ;
    }

    @Override
    public void insertRoute(ModelRoute route) {
        modelProviderMapper.insert(route);
    }

    @Override
    public void updateRoute(ModelRoute route) {
        modelProviderMapper.updateById(route);
    }

    @Override
    public void deleteRoutesByProvider(String providerCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelRoute>().eq(ModelRoute::getProviderCode, providerCode));
    }

    @Override
    public void deleteRoutesByModel(String modelCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelRoute>().eq(ModelRoute::getModelCode, modelCode));
    }

    @Override
    public void deleteRoute(String modelCode, String providerCode) {
        modelProviderMapper.delete(
                new LambdaQueryWrapper<ModelRoute>()
                        .eq(ModelRoute::getModelCode, modelCode)
                        .eq(ModelRoute::getProviderCode, providerCode));
    }






}
