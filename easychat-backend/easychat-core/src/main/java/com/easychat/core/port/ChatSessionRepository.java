package com.easychat.core.port;

import com.easychat.core.domain.chat.ChatSession;

import java.util.List;

public interface ChatSessionRepository {
    ChatSession insert(ChatSession session);

    ChatSession findById(Long id);

    ChatSession findBySessionCode(String sessionCode);

    List<ChatSession> findAll();

    void update(ChatSession session);

    void deleteById(Long id);
}
