package com.easychat.core.router;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easychat.infra.mysql.entity.ModelProviderDO;
import com.easychat.infra.mysql.entity.ProviderDO;
import com.easychat.infra.mysql.mapper.ModelProviderMapper;
import com.easychat.infra.mysql.mapper.ProviderMapper;
import com.easychat.llm.provider.OpenAICompatibleProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 渠道注册中心：启动时从 DB 加载渠道配置，并每 5 分钟热刷新一次。
 * 缓存格式：modelCode → 按优先级排序的 ProviderWrapper 列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderRegistry {

    private final ModelProviderMapper modelProviderMapper;
    private final ProviderMapper providerMapper;

    /** model_code → sorted ProviderWrapper list */
    private volatile Map<String, List<ProviderWrapper>> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reload() {
        try {
            Map<String, ProviderDO> providerMap = providerMapper
                    .selectList(new LambdaQueryWrapper<ProviderDO>().eq(ProviderDO::getEnabled, 1))
                    .stream()
                    .collect(Collectors.toMap(ProviderDO::getProviderCode, p -> p));

            List<ModelProviderDO> configs = modelProviderMapper
                    .selectList(new LambdaQueryWrapper<ModelProviderDO>().eq(ModelProviderDO::getEnabled, 1));

            Map<String, List<ProviderWrapper>> newCache = configs.stream()
                    .filter(cfg -> providerMap.containsKey(cfg.getProviderCode()))
                    .collect(Collectors.groupingBy(
                            ModelProviderDO::getModelCode,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream()
                                            .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                                            .map(cfg -> buildWrapper(cfg, providerMap.get(cfg.getProviderCode())))
                                            .collect(Collectors.toList())
                            )
                    ));

            cache = new ConcurrentHashMap<>(newCache);
            log.info("ProviderRegistry reloaded: {} model(s) configured", cache.size());
        } catch (Exception e) {
            log.error("ProviderRegistry reload failed", e);
        }
    }

    public List<ProviderWrapper> getProviders(String modelCode) {
        return cache.getOrDefault(modelCode, Collections.emptyList());
    }

    private ProviderWrapper buildWrapper(ModelProviderDO cfg, ProviderDO provider) {
        var llmProvider = new OpenAICompatibleProvider(
                cfg.getProviderCode(),
                provider.getApiKey(),
                provider.getBaseUrl(),
                cfg.getModelCode(),    // modelName = modelCode（可扩展）
                0.7,
                cfg.getModelCode() != null ? 2000 : 2000,
                cfg.getTimeoutMs() != null ? cfg.getTimeoutMs() : 60_000
        );
        return new ProviderWrapper(
                llmProvider,
                cfg.getPriority() != null ? cfg.getPriority() : 0,
                cfg.getWeight() != null ? cfg.getWeight() : 1,
                cfg.getCircuitBreakerThreshold() != null ? cfg.getCircuitBreakerThreshold() : 3,
                cfg.getCircuitBreakerWindow() != null ? cfg.getCircuitBreakerWindow() : 30
        );
    }
}
