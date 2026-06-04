package com.easychat.core.event;

import com.easychat.core.port.ChatEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class NoopChatEventPublisher implements ChatEventPublisher {
    @Override
    public void publish(ChatEvent event) {
        // Default publisher; infrastructure adapters can publish to an external event bus later.
    }
}
