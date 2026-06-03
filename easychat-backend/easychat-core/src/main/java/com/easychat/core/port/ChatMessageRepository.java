package com.easychat.core.port;

import com.easychat.core.domain.chat.ChatMessage;

import java.util.List;

public interface ChatMessageRepository {
    ChatMessage insert(ChatMessage message);

    void update(ChatMessage message);

    void deleteBySessionId(Long sessionId);

    List<ChatMessage> findBySessionId(Long sessionId);

    int nextMessageOrder(Long sessionId);
}
