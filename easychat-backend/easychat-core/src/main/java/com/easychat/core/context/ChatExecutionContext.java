package com.easychat.core.context;

import lombok.Data;

@Data
public class ChatExecutionContext {
    private String traceId;
    private String userId;
    private String tenantId;
    private String requestId;
    private String sessionCode;
    private Long sessionId;
    private String modelCode;
    private String providerCode;
    private boolean toolsEnabled;
    private boolean ragEnabled;

    public AgentContext toAgentContext(String userMessage) {
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.setUserMessage(userMessage);
        context.setToolsEnabled(toolsEnabled);
        context.setRagEnabled(ragEnabled);
        context.setVariable("chatExecutionContext", this);
        context.setVariable("modelCode", modelCode);
        return context;
    }
}
