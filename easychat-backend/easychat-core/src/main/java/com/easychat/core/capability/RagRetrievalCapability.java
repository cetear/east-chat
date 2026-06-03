package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class RagRetrievalCapability implements AgentCapability {
    @Override
    public boolean supports(ChatExecutionContext context) {
        return context != null && context.isRagEnabled();
    }
}
