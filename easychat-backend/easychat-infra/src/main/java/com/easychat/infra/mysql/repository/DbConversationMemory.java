package com.easychat.infra.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.entity.ChatSessionMemoryDO;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import com.easychat.infra.mysql.mapper.ChatSessionMemoryMapper;
import com.easychat.common.port.ConversationMemory;
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
    @Autowired private com.easychat.common.port.ChatMessageRepository messages;

    @Autowired
    private ChatSessionMemoryMapper chatSessionMemoryMapper;

    @Override
    public List<ChatMessage> getRecentMessages(Long sessionId, int maxRounds) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
               .eq("status", com.easychat.common.constant.MessageStatus.SUCCESS).gt("id", coveredMessageId(sessionId))
               .orderByDesc("message_order")
               .last("LIMIT " + (maxRounds * 2));

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        // 反转顺序，使其按时间正序
        java.util.Collections.reverse(messages);
        return messages;
    }

    @Override
    public int countMessages(Long sessionId) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId).eq("status", com.easychat.common.constant.MessageStatus.SUCCESS).gt("id", coveredMessageId(sessionId));
        return Math.toIntExact(chatMessageMapper.selectCount(wrapper));
    }

    @Override
    public String getSummary(Long sessionId) {
        ChatSessionMemoryDO memory = selectLatest(sessionId);
        return memory != null ? memory.getSummary() : null;
    }

    @Override
    public void saveSummary(Long sessionId, String summary) {
        saveSummary(sessionId, summary, coveredMessageId(sessionId));
    }

    @Override
    public void saveSummary(Long sessionId, String summary, long coveredMessageId) {
        ChatSessionMemoryDO existing = selectLatest(sessionId);
        if (existing == null) {
            ChatSessionMemoryDO memory = new ChatSessionMemoryDO();
            memory.setSessionId(sessionId);
            memory.setSummary(summary);
            memory.setVersion(1);
            memory.setCoveredMessageId(coveredMessageId);
            memory.setCreatedAt(LocalDateTime.now());
            chatSessionMemoryMapper.insert(memory);
            return;
        }
        existing.setSummary(summary);
        existing.setCoveredMessageId(coveredMessageId);
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
    public void clear(Long sessionId) {
        messages.deleteBySessionId(sessionId);
        chatSessionMemoryMapper.delete(new QueryWrapper<ChatSessionMemoryDO>().eq("session_id", sessionId));
    }

    @Override public long coveredMessageId(Long sessionId) {
        ChatSessionMemoryDO latest = selectLatest(sessionId);
        return latest == null || latest.getCoveredMessageId() == null ? 0 : latest.getCoveredMessageId();
    }

    @Override public List<ChatMessage> unsummarized(Long sessionId, int limit) {
        return chatMessageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", sessionId)
                .eq("status", com.easychat.common.constant.MessageStatus.SUCCESS).gt("id", coveredMessageId(sessionId)).orderByAsc("id").last("LIMIT " + Math.max(1, limit)));
    }
}
