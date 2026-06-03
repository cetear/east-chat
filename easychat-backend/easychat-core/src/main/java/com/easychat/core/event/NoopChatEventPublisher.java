package com.easychat.core.event;

import com.easychat.core.port.ChatEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ChatEventPublisher.class)
public class NoopChatEventPublisher implements ChatEventPublisher {
    @Override
    public void publish(ChatEvent event) {
        // Default publisher; infrastructure adapters can publish to an external event bus later.
    }
}
