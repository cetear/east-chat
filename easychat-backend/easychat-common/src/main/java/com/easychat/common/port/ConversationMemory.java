package com.easychat.common.port;

import com.easychat.common.domain.chat.ChatMessage;

import java.util.List;

public interface ConversationMemory {
    /**
     * 获取最近的消息
     */
    List<ChatMessage> getRecentMessages(Long sessionId, int maxRounds);

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
     * 清空会话记忆
     */
    void clear(Long sessionId);

    long coveredMessageId(Long sessionId);
    List<ChatMessage> unsummarized(Long sessionId, int limit);
    void saveSummary(Long sessionId, String summary, long coveredMessageId);
}
