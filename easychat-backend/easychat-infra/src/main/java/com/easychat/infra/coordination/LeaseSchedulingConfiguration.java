package com.easychat.infra.coordination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** A slow event subscriber or registry refresh must not starve lease renewal. */
@Configuration
@ConditionalOnProperty(name="easychat.distributed.enabled",havingValue="true")
public class LeaseSchedulingConfiguration {
    @Bean("leaseScheduler")
    public ThreadPoolTaskScheduler leaseScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("lease-renew-");
        return scheduler;
    }
    @Bean("taskScheduler")
    @ConditionalOnMissingBean(name="taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("background-");
        return scheduler;
    }
}
