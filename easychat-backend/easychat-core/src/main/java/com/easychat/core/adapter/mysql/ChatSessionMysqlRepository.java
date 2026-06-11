package com.easychat.core.adapter.mysql;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.exception.BusinessException;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.port.ChatSessionRepository;
import com.easychat.infra.mysql.entity.ChatSessionDO;
import com.easychat.infra.mysql.mapper.ChatSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatSessionMysqlRepository implements ChatSessionRepository {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSession insert(ChatSession session) {
        ChatSessionDO entity = toEntity(session);
        chatSessionMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public ChatSession findById(Long id) {
        return toDomain(chatSessionMapper.selectById(id));
    }

    @Override
    public ChatSession findBySessionCode(String sessionCode) {
        return toDomain(chatSessionMapper.selectOne(
                new QueryWrapper<ChatSessionDO>().eq("session_code", sessionCode)));
    }

    @Override
    public List<ChatSession> findAll() {
        return chatSessionMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void update(ChatSession session) {
        if (session.getId() == null) {
            throw new BusinessException("Session id is required for update");
        }
        chatSessionMapper.updateById(toEntity(session));
    }

    @Override
    public void deleteById(Long id) {
        chatSessionMapper.deleteById(id);
    }

    private ChatSession toDomain(ChatSessionDO entity) {
        if (entity == null) {
            return null;
        }
        ChatSession session = new ChatSession();
        session.setId(entity.getId());
        session.setSessionCode(entity.getSessionCode());
        session.setTitle(entity.getTitle());
        session.setSystemPrompt(entity.getSystemPrompt());
        session.setMaxRounds(entity.getMaxRounds());
        session.setStatus(entity.getStatus());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }

    private ChatSessionDO toEntity(ChatSession session) {
        ChatSessionDO entity = new ChatSessionDO();
        entity.setId(session.getId());
        entity.setSessionCode(session.getSessionCode());
        entity.setTitle(session.getTitle());
        entity.setSystemPrompt(session.getSystemPrompt());
        entity.setMaxRounds(session.getMaxRounds());
        entity.setStatus(session.getStatus());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        return entity;
    }
}
