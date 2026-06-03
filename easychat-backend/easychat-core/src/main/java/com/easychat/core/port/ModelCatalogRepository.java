package com.easychat.core.port;

import com.easychat.core.domain.model.ModelDefinition;
import com.easychat.core.domain.model.ModelRoute;
import com.easychat.core.domain.model.ProviderAccount;

import java.util.List;

public interface ModelCatalogRepository {
    ProviderAccount findProviderByCode(String providerCode);

    ProviderAccount findProviderById(Long id);

    List<ProviderAccount> findProviders();

    void insertProvider(ProviderAccount provider);

    void updateProvider(ProviderAccount provider);

    void deleteProviderById(Long id);

    ModelDefinition findModelByCode(String modelCode);

    void insertModel(ModelDefinition model);

    void updateModel(ModelDefinition model);

    void deleteModelById(Long id);

    ModelRoute findRoute(String modelCode, String providerCode);

    List<ModelRoute> findRoutesByProvider(String providerCode);

    void insertRoute(ModelRoute route);

    void deleteRoutesByProvider(String providerCode);

    void deleteRoutesByModel(String modelCode);

    void deleteRoute(String modelCode, String providerCode);
}
