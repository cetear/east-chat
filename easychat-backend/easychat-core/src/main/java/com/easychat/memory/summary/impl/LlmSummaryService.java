package com.easychat.memory.summary.impl;

import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.llm.client.LLMClient;
import com.easychat.memory.ConversationMemory;
import com.easychat.memory.summary.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmSummaryService implements SummaryService {

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private LLMClient llmClient;

    @Value("${easychat.memory.summary-threshold:10}")
    private int threshold;

    @Value("${easychat.memory.summary-max-messages:40}")
    private int maxMessages;

    @Override
    public boolean shouldSummarize(Long sessionId, int messageCount) {
        return messageCount >= threshold;
    }

    @Override
    public String generateSummary(Long sessionId) {
        List<ChatMessageDO> messages = conversationMemory.getRecentMessages(sessionId, maxMessages / 2);
        if (messages.isEmpty()) {
            return null;
        }
        String transcript = messages.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
        String prompt = "请用简洁的中文总结以下对话，保留关键事实、用户偏好与未完成任务：\n\n" + transcript;
        try {
            return llmClient.chat(prompt);
        } catch (Exception e) {
            log.warn("Summary generation failed for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
