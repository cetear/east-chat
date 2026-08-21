package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.memory.ConversationMemory;
import com.easychat.memory.summary.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
public class ConversationMemoryCapability implements AgentCapability {

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private SummaryService summaryService;

    @Override
    public boolean supports(ChatExecutionContext context) {
        return true;
    }

    @Override
    public void afterRun(AgentResponse response, ChatExecutionContext context) {
        if (context == null || context.getSessionId() == null) {
            return;
        }
        try {
            int count = conversationMemory.countMessages(context.getSessionId());
            if (!summaryService.shouldSummarize(context.getSessionId(), count)) {
                return;
            }
            String summary = summaryService.generateSummary(context.getSessionId());
            if (summary != null && !summary.isBlank()) {
                conversationMemory.saveSummary(context.getSessionId(), summary);
            }
        } catch (Exception e) {
            log.warn("Conversation summarization failed for session {}: {}",
                context.getSessionId(), e.getMessage());
        }
    }
}
