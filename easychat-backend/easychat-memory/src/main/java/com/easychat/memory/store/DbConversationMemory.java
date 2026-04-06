package com.easychat.memory.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import com.easychat.memory.ConversationMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DbConversationMemory implements ConversationMemory {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

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
    public void addMessage(ChatMessageDO message) {
        message.setCreatedAt(System.currentTimeMillis());
        chatMessageMapper.insert(message);
    }

    @Override
    public void clear(Long sessionId) {
        QueryWrapper<ChatMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        chatMessageMapper.delete(wrapper);
    }
}
