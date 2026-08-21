package com.easychat.memory.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.infra.mysql.entity.ChatSessionMemoryDO;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import com.easychat.infra.mysql.mapper.ChatSessionMemoryMapper;
import com.easychat.memory.ConversationMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class DbConversationMemory implements ConversationMemory {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMemoryMapper chatSessionMemoryMapper;

    @Override
    public List<ChatMessageDO> getRecentMessages(Long sessionId, int maxRounds) {
        QueryWrapper<ChatMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
               .orderByDesc("message_order")
               .last("LIMIT " + (maxRounds * 2));

        List<ChatMessageDO> messages = chatMessageMapper.selectList(wrapper);
        // 反转顺序，使其按时间正序
        java.util.Collections.reverse(messages);
        return messages;
    }

    @Override
    public int countMessages(Long sessionId) {
        QueryWrapper<ChatMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        return Math.toIntExact(chatMessageMapper.selectCount(wrapper));
    }

    @Override
    public String getSummary(Long sessionId) {
        ChatSessionMemoryDO memory = selectLatest(sessionId);
        return memory != null ? memory.getSummary() : null;
    }

    @Override
    public void saveSummary(Long sessionId, String summary) {
        ChatSessionMemoryDO existing = selectLatest(sessionId);
        if (existing == null) {
            ChatSessionMemoryDO memory = new ChatSessionMemoryDO();
            memory.setSessionId(sessionId);
            memory.setSummary(summary);
            memory.setVersion(1);
            memory.setCreatedAt(LocalDateTime.now());
            chatSessionMemoryMapper.insert(memory);
            return;
        }
        existing.setSummary(summary);
        existing.setVersion((existing.getVersion() != null ? existing.getVersion() : 1) + 1);
        chatSessionMemoryMapper.updateById(existing);
    }

    private ChatSessionMemoryDO selectLatest(Long sessionId) {
        QueryWrapper<ChatSessionMemoryDO> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
               .orderByDesc("version")
               .last("LIMIT 1");
        return chatSessionMemoryMapper.selectOne(wrapper);
    }

    @Override
    public void addMessage(ChatMessageDO message) {
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }

    @Override
    public void clear(Long sessionId) {
        QueryWrapper<ChatMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        chatMessageMapper.delete(wrapper);
    }
}
