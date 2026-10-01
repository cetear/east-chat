package com.easychat.core.router;

import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
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

    private final com.easychat.infra.mysql.repository.RouteCatalog catalog;
    @org.springframework.beans.factory.annotation.Autowired private List<com.easychat.llm.provider.ProviderFactory> factories;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.core.env.Environment environment;

    /** model_code → sorted ProviderWrapper list */
    private volatile Map<String, List<ProviderWrapper>> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public synchronized void reload() {
        try {
            Map<String, ProviderAccount> providerMap = catalog.providers()
                    .stream()
                    .collect(Collectors.toMap(ProviderAccount::getProviderCode, p -> p));

            List<ModelRoute> configs = catalog.routes();

            Map<String, ModelDefinition> modelMap = catalog.models()
                    .stream()
                    .collect(Collectors.toMap(ModelDefinition::getModelCode, m -> m));

            Map<String, List<ProviderWrapper>> newCache = configs.stream()
                    .filter(cfg -> providerMap.containsKey(cfg.getProviderCode()))
                    .filter(cfg -> modelMap.containsKey(cfg.getModelCode()))
                    .collect(Collectors.groupingBy(
                            ModelRoute::getModelCode,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream()
                                            .sorted(java.util.Comparator.comparingInt(a -> a.getPriority() == null ? 0 : a.getPriority()))
                                            .map(cfg -> buildWrapper(
                                                    cfg,
                                                    providerMap.get(cfg.getProviderCode()),
                                                    modelMap.get(cfg.getModelCode())))
                                            .collect(Collectors.toList())
                            )
                    ));

            cache = new ConcurrentHashMap<>(newCache);
            log.info("ProviderRegistry reloaded: {} model(s) configured", cache.size());
        } catch (Exception e) {
            cache = new ConcurrentHashMap<>();
            log.error("ProviderRegistry reload failed", e);
        }
    }

    public List<ProviderWrapper> getProviders(String modelCode) {
        return cache.getOrDefault(modelCode, Collections.emptyList());
    }

    public boolean isModelEnabled(String code) {
        return catalog.enabled(code);
    }

    private ProviderWrapper buildWrapper(ModelRoute cfg, ProviderAccount provider, ModelDefinition model) {
        String protocol=environment.getProperty("easychat.providers.protocols."+provider.getProviderCode(),"openai-compatible");
        var matches=factories.stream().filter(f -> f.protocol().equals(protocol)).toList();
        if(matches.size()!=1) throw new IllegalArgumentException("Expected exactly one provider factory for protocol: "+protocol);
        var llmProvider=matches.get(0).create(provider,model,cfg);
        ProviderWrapper wrapper = new ProviderWrapper(
                llmProvider,
                cfg.getPriority() != null ? cfg.getPriority() : 0,
                cfg.getWeight() != null ? cfg.getWeight() : 1,
                3,
                30,
                cfg.getMaxRetry() == null ? 0 : cfg.getMaxRetry()
        );
        wrapper.setVision(environment.getProperty("easychat.providers.capabilities."+provider.getProviderCode()+".vision",Boolean.class,Integer.valueOf(1).equals(model.getSupportVision())));
        wrapper.setVisionTools(environment.getProperty("easychat.providers.capabilities."+provider.getProviderCode()+".vision-tools",Boolean.class,true));
        cache.getOrDefault(cfg.getModelCode(), List.of()).stream()
            .filter(old -> old.getProvider().getProviderCode().equals(cfg.getProviderCode()))
            .findFirst().ifPresent(wrapper::inheritState);
        return wrapper;
    }

    public Map<String,List<Map<String,Object>>> diagnostics() {
        return cache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->e.getValue().stream().map(ProviderWrapper::diagnostics).toList()));
    }
    @Scheduled(fixedDelay=30000) public void persistCircuitSnapshots() {
        var routes=cache.values().stream().flatMap(List::stream).collect(Collectors.groupingBy(r->r.getProvider().getProviderCode()));
        routes.forEach((code,entries)->{
            try {
                int failures=entries.stream().mapToInt(r->r.getFailCount().get()).max().orElse(0);
                String status=entries.stream().anyMatch(r->"OPEN".equals(r.getCircuitStatus()))?"OPEN":entries.stream().anyMatch(r->"HALF_OPEN".equals(r.getCircuitStatus()))?"HALF_OPEN":"CLOSED";
                long last=entries.stream().mapToLong(ProviderWrapper::getLastFailTimeMs).max().orElse(0);
                catalog.snapshot(code,failures,status,last==0?null:java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(last),java.time.ZoneId.systemDefault()));
            }catch(Exception e){log.warn("Circuit snapshot persistence failed for {}",code);}
        });
    }
    private Double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
