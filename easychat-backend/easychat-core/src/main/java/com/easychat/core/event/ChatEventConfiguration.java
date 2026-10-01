package com.easychat.core.event;

import com.easychat.core.port.ChatEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ChatEventConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChatEventPublisher.class)
    public ChatEventPublisher chatEventPublisher(ApplicationEventPublisher publisher,
                                                 @Value("${easychat.events.enabled:true}") boolean enabled) {
        if (!enabled) return new NoopChatEventPublisher();
        return event -> {
            try {
                publisher.publishEvent(event);
                log.info("Chat event {} session={} message={}", event.getClass().getSimpleName(), event.getSessionId(), event.getMessageId());
            } catch (Exception e) {
                log.warn("Chat event listener failed", e);
            }
        };
    }
}
