package com.easychat.infra.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class ChatEventProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "chat-events";

    public void sendChatEvent(String sessionId, String message) {
        try {
            String event = String.format("{\"sessionId\":\"%s\",\"message\":\"%s\"}", sessionId, message);
            kafkaTemplate.send(TOPIC, sessionId, event);
            log.info("Sent chat event to Kafka: {}", event);
        } catch (Exception e) {
            log.error("Failed to send chat event to Kafka", e);
        }
    }
}
