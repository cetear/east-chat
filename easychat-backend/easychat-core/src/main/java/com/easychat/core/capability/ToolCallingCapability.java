package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ToolCallingCapability implements AgentCapability {
    @Override
    public boolean supports(ChatExecutionContext context) {
        return context != null && context.isToolsEnabled();
    }
}
