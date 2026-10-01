package com.easychat.infra.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.common.port.ChatSessionRepository;
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
        ChatSession entity = session;
        chatSessionMapper.insert(entity);
        return entity;
    }

    @Override
    public ChatSession findById(Long id) {
        return chatSessionMapper.selectById(id);
    }

    @Override
    public ChatSession findBySessionCode(String sessionCode) {
        return chatSessionMapper.selectOne(
                new QueryWrapper<ChatSession>().eq("session_code", sessionCode));
    }

    @Override
    public List<ChatSession> findByOwner(String owner) {
        return chatSessionMapper.selectList(new QueryWrapper<ChatSession>().eq("owner_id", owner));
    }

    public List<ChatSession> findAll() {
        return chatSessionMapper.selectList(null);
    }

    @Override
    public void update(ChatSession session) {
        if (session.getId() == null) {
            throw new BusinessException("Session id is required for update");
        }
        chatSessionMapper.updateById(session);
    }

    @Override
    public void deleteById(Long id) {
        chatSessionMapper.deleteById(id);
    }


}
