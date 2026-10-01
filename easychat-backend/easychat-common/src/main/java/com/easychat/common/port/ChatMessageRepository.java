package com.easychat.common.port;

import com.easychat.common.domain.chat.ChatMessage;

import java.util.List;

public interface ChatMessageRepository {
    ChatMessage insert(ChatMessage message);

    void update(ChatMessage message);

    void deleteBySessionId(Long sessionId);

    List<ChatMessage> findBySessionId(Long sessionId);

    long countSuccessful(Long sessionId);

    int nextMessageOrder(Long sessionId);
}
