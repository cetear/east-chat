package com.easychat.core.adapter.mysql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.exception.BusinessException;
import com.easychat.core.domain.chat.ChatMessage;
import com.easychat.core.port.ChatMessageRepository;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatMessageMysqlRepository implements ChatMessageRepository {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessage insert(ChatMessage message) {
        ChatMessageDO entity = toEntity(message);
        chatMessageMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public void update(ChatMessage message) {
        if (message.getId() == null) {
            throw new BusinessException("Message id is required for update");
        }
        chatMessageMapper.updateById(toEntity(message));
    }

    @Override
    public void deleteBySessionId(Long sessionId) {
        chatMessageMapper.delete(new QueryWrapper<ChatMessageDO>().eq("session_id", sessionId));
    }

    @Override
    public List<ChatMessage> findBySessionId(Long sessionId) {
        return chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageDO>()
                                .eq(ChatMessageDO::getSessionId, sessionId)
                                .orderByAsc(ChatMessageDO::getMessageOrder))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int nextMessageOrder(Long sessionId) {
        return chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageDO>().eq(ChatMessageDO::getSessionId, sessionId))
                .stream()
                .map(ChatMessageDO::getMessageOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private ChatMessage toDomain(ChatMessageDO entity) {
        if (entity == null) {
            return null;
        }
        ChatMessage message = new ChatMessage();
        message.setId(entity.getId());
        message.setSessionId(entity.getSessionId());
        message.setRole(entity.getRole());
        message.setContent(entity.getContent());
        message.setContentType(entity.getContentType());
        message.setMessageOrder(entity.getMessageOrder());
        message.setModelCode(entity.getModelCode());
        message.setProviderCode(entity.getProviderCode());
        message.setStatus(entity.getStatus());
        message.setErrorMsg(entity.getErrorMsg());
        message.setCreatedAt(entity.getCreatedAt());
        return message;
    }

    private ChatMessageDO toEntity(ChatMessage message) {
        ChatMessageDO entity = new ChatMessageDO();
        entity.setId(message.getId());
        entity.setSessionId(message.getSessionId());
        entity.setRole(message.getRole());
        entity.setContent(message.getContent());
        entity.setContentType(message.getContentType());
        entity.setMessageOrder(message.getMessageOrder());
        entity.setModelCode(message.getModelCode());
        entity.setProviderCode(message.getProviderCode());
        entity.setStatus(message.getStatus());
        entity.setErrorMsg(message.getErrorMsg());
        entity.setCreatedAt(message.getCreatedAt());
        return entity;
    }
}
