package com.easychat.core.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public abstract class ChatEvent {
    private String eventId = UUID.randomUUID().toString();
    private LocalDateTime occurredAt = LocalDateTime.now();
    private Long sessionId;
    private String sessionCode;
    private Long messageId;
    private String modelCode;
    private String providerCode;
}
