package com.easychat.common.port;

import com.easychat.common.domain.chat.ChatSession;

import java.util.List;

public interface ChatSessionRepository {
    ChatSession insert(ChatSession session);

    ChatSession findById(Long id);

    ChatSession findBySessionCode(String sessionCode);

    List<ChatSession> findAll();
    default List<ChatSession> findByOwner(String owner) {return findAll().stream().filter(s->java.util.Objects.equals(owner,s.getOwnerId())).toList();}

    void update(ChatSession session);

    void deleteById(Long id);
}
