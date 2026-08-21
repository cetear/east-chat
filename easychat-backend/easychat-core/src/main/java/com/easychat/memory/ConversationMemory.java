package com.easychat.memory;

import com.easychat.infra.mysql.entity.ChatMessageDO;

import java.util.List;

public interface ConversationMemory {
    /**
     * 获取最近的消息
     */
    List<ChatMessageDO> getRecentMessages(Long sessionId, int maxRounds);

    /**
     * 统计会话消息数量
     */
    int countMessages(Long sessionId);

    /**
     * 获取会话最新摘要
     */
    String getSummary(Long sessionId);

    /**
     * 保存会话摘要
     */
    void saveSummary(Long sessionId, String summary);

    /**
     * 添加消息
     */
    void addMessage(ChatMessageDO message);

    /**
     * 清空会话记忆
     */
    void clear(Long sessionId);
}
