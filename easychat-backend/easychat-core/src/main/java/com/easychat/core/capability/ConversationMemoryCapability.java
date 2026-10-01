package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.memory.summary.SummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(10)
public class ConversationMemoryCapability implements AgentCapability {
    @Autowired
    private SummaryService summaryService;
    @Value("${easychat.memory.summary-enabled:true}")
    private boolean enabled = true;

    public boolean supports(ChatExecutionContext context) {
        return enabled;
    }

    public void afterRun(AgentResponse response, ChatExecutionContext context) {
        if (!response.isSuccess()) return;
        try {
            summaryService.summarize(context);
        } catch (Exception e) {
            log.warn("Summary failed for session {}: {}", context.getSessionId(), e.getMessage());
        }
    }
}
