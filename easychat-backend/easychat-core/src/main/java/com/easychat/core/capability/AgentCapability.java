package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;

public interface AgentCapability {
    boolean supports(ChatExecutionContext context);

    default void beforeRun(AgentRequest request, ChatExecutionContext context) {
    }

    default void afterRun(AgentResponse response, ChatExecutionContext context) {
    }
}
