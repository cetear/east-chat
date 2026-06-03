package com.easychat.core.capability;

import com.easychat.core.context.ChatExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentCapabilityRegistry {

    private final List<AgentCapability> capabilities;

    @Autowired
    public AgentCapabilityRegistry(List<AgentCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public void beforeRun(AgentRequest request, ChatExecutionContext context) {
        capabilities.stream()
                .filter(capability -> capability.supports(context))
                .forEach(capability -> capability.beforeRun(request, context));
    }

    public void afterRun(AgentResponse response, ChatExecutionContext context) {
        capabilities.stream()
                .filter(capability -> capability.supports(context))
                .forEach(capability -> capability.afterRun(response, context));
    }
}
