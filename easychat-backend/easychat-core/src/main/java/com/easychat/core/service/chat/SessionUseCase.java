package com.easychat.core.service.chat;

import com.easychat.common.exception.BusinessException;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.common.port.ChatMessageRepository;
import com.easychat.common.port.ChatSessionRepository;
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
    @Autowired private com.easychat.common.port.ConversationMemory memory;
    @Autowired private SessionExecutionLock locks;

    public ChatSession createSession() {return createSession(com.easychat.common.security.ActorContext.current());}
    public ChatSession createSession(com.easychat.common.security.Actor actor) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setSessionCode(UUID.randomUUID().toString()); session.setOwnerId(actor.userId());
        session.setTitle("New Chat");
        session.setMaxRounds(10);
        session.setStatus(com.easychat.common.constant.SessionStatus.OPEN);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return chatSessionRepository.insert(session);
    }

    public ChatSession getSession(String sessionCode) {return getSession(sessionCode,com.easychat.common.security.ActorContext.current());}
    public ChatSession getSession(String sessionCode,com.easychat.common.security.Actor actor) {
        ChatSession session = chatSessionRepository.findBySessionCode(sessionCode);
        if (session == null || !java.util.Objects.equals(session.getOwnerId(), actor.userId())) {
            throw new BusinessException("404", "Session not found: " + sessionCode);
        }
        return session;
    }

    public ChatSession getSessionById(Long sessionId) {
        var actor=com.easychat.common.security.ActorContext.current();
        ChatSession session = chatSessionRepository.findById(sessionId);
        if (session == null || !java.util.Objects.equals(session.getOwnerId(), actor.userId())) {
            throw new BusinessException("404", "Session not found: " + sessionId);
        }
        return session;
    }

    public List<ChatSession> getSessions() {
        return chatSessionRepository.findByOwner(com.easychat.common.security.ActorContext.current().userId());
    }

    public void updateSession(ChatSession session) {
        if (session.getMaxRounds() != null && (session.getMaxRounds() < 1 || session.getMaxRounds() > 100))
            throw new IllegalArgumentException("maxRounds must be 1-100");
        if (session.getStatus() != null && session.getStatus() != com.easychat.common.constant.SessionStatus.CLOSED && session.getStatus() != com.easychat.common.constant.SessionStatus.OPEN)
            throw new IllegalArgumentException("status must be 0 or 1");
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

    @org.springframework.transaction.annotation.Transactional
    public void deleteSession(String sessionCode) {
        ChatSession session = getSession(sessionCode);
        try (var lease = locks.acquire(session.getId())) {
            lease.check();
            memory.clear(session.getId());
            chatSessionRepository.deleteById(session.getId());
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public List<com.easychat.common.domain.chat.ChatMessage> history(String sessionCode) {
        return chatMessageRepository.findBySessionId(getSession(sessionCode).getId());
    }

    public void setMaxRounds(String sessionCode, Integer maxRounds) {
        ChatSession session = getSession(sessionCode);
        session.setMaxRounds(maxRounds);
        updateSession(session);
    }
}
