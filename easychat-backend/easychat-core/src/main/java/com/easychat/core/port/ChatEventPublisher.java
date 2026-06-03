package com.easychat.core.port;

import com.easychat.core.event.ChatEvent;

public interface ChatEventPublisher {
    void publish(ChatEvent event);
}
