package com.easychat.core.router;

import com.easychat.llm.provider.LLMProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 包装单个渠道商，附带熔断状态和路由元数据。
 * 熔断采用简单的滑动计数策略：{@code failCount} 超过阈值后进入冷却期，
 * 冷却期结束后自动半开放（允许一次探测）。
 */
@Slf4j
@Getter
public class ProviderWrapper {

    private final LLMProvider provider;
    private final int priority;
    private final int weight;
    private final int circuitBreakerThreshold;
    /** 熔断冷却时间（毫秒） */
    private final long circuitBreakerWindowMs;

    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile long lastFailTimeMs = 0L;

    public ProviderWrapper(LLMProvider provider,
                           int priority,
                           int weight,
                           int circuitBreakerThreshold,
                           int circuitBreakerWindowSec) {
        this.provider = provider;
        this.priority = priority;
        this.weight = weight;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.circuitBreakerWindowMs = (long) circuitBreakerWindowSec * 1000;
    }

    /**
     * 判断当前渠道是否可用（未熔断）。
     * 超过失败阈值且仍在冷却窗口内 → 不可用；冷却结束后重置状态。
     */
    public boolean isAvailable() {
        if (failCount.get() < circuitBreakerThreshold) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastFailTimeMs;
        if (elapsed >= circuitBreakerWindowMs) {
            // 冷却结束，重置半开放
            failCount.set(0);
            log.info("[{}] Circuit breaker reset after cooldown", provider.getProviderCode());
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        failCount.set(0);
    }

    public void recordFailure() {
        lastFailTimeMs = System.currentTimeMillis();
        int count = failCount.incrementAndGet();
        if (count >= circuitBreakerThreshold) {
            log.warn("[{}] Circuit breaker OPEN (failures={})", provider.getProviderCode(), count);
        }
    }
}
