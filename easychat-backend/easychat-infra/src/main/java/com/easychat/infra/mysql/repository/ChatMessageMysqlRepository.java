package com.easychat.infra.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ChatMessageRepository;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatMessageMysqlRepository implements ChatMessageRepository {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private com.easychat.infra.mysql.mapper.ChatSessionMapper chatSessionMapper;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ChatMessage insert(ChatMessage message) {
        if (chatSessionMapper.selectOne(new QueryWrapper<com.easychat.common.domain.chat.ChatSession>()
                .eq("id", message.getSessionId()).last("FOR UPDATE")) == null)
            throw new BusinessException("Session not found");
        message.setMessageOrder(nextMessageOrder(message.getSessionId()));
        ChatMessage entity = message;
        chatMessageMapper.insert(entity);
        return entity;
    }

    @Override
    public void update(ChatMessage message) {
        if (message.getId() == null) {
            throw new BusinessException("Message id is required for update");
        }
        chatMessageMapper.updateById(message);
    }

    @Override
    public void deleteBySessionId(Long sessionId) {
        chatMessageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
    }

    @Override
    public List<ChatMessage> findBySessionId(Long sessionId) {
        return chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getMessageOrder))
                ;
    }

    @Override public long countSuccessful(Long id) {return chatMessageMapper.selectCount(new QueryWrapper<ChatMessage>().eq("session_id",id).eq("status",com.easychat.common.constant.MessageStatus.SUCCESS));}

    @Override
    public int nextMessageOrder(Long sessionId) {
        ChatMessage last = chatMessageMapper.selectOne(new QueryWrapper<ChatMessage>()
                .eq("session_id", sessionId).orderByDesc("message_order").last("LIMIT 1"));
        return last == null ? 0 : last.getMessageOrder() + 1;
    }


}
