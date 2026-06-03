package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class ToolCallingCapability implements AgentCapability {
    @Override
    public boolean supports(ChatExecutionContext context) {
        return context != null && context.isToolsEnabled();
    }
}
