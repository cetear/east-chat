package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class ConversationMemoryCapability implements AgentCapability {
    @Override
    public boolean supports(ChatExecutionContext context) {
        return true;
    }
}
