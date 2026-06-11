package com.easychat.core.service.chat;

import com.easychat.common.exception.BusinessException;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.port.ChatMessageRepository;
import com.easychat.core.port.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SessionUseCase {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    public ChatSession createSession() {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setSessionCode(UUID.randomUUID().toString());
        session.setTitle("New Chat");
        session.setMaxRounds(10);
        session.setStatus(1);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return chatSessionRepository.insert(session);
    }

    public ChatSession getSession(String sessionCode) {
        ChatSession session = chatSessionRepository.findBySessionCode(sessionCode);
        if (session == null) {
            throw new BusinessException("Session not found: " + sessionCode);
        }
        return session;
    }

    public ChatSession getSessionById(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId);
        if (session == null) {
            throw new BusinessException("Session not found: " + sessionId);
        }
        return session;
    }

    public List<ChatSession> getSessions() {
        return chatSessionRepository.findAll();
    }

    public void updateSession(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.update(session);
    }

    public ChatSession updateSession(String sessionCode, ChatSession changes) {
        ChatSession session = getSession(sessionCode);
        if (changes.getTitle() != null) {
            session.setTitle(changes.getTitle());
        }
        if (changes.getSystemPrompt() != null) {
            session.setSystemPrompt(changes.getSystemPrompt());
        }
        if (changes.getMaxRounds() != null) {
            session.setMaxRounds(changes.getMaxRounds());
        }
        if (changes.getStatus() != null) {
            session.setStatus(changes.getStatus());
        }
        updateSession(session);
        return session;
    }

    public void deleteSession(String sessionCode) {
        ChatSession session = getSession(sessionCode);
        chatMessageRepository.deleteBySessionId(session.getId());
        chatSessionRepository.deleteById(session.getId());
    }

    public void setMaxRounds(String sessionCode, Integer maxRounds) {
        ChatSession session = getSession(sessionCode);
        session.setMaxRounds(maxRounds);
        updateSession(session);
    }
}
