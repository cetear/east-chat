package com.easychat.core.context;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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
    private Integer maxOutputTokens;
    private BigDecimal defaultTemperature;
    private BigDecimal defaultTopP;
    private String defaultConfig;
    private boolean toolsEnabled;
    private boolean ragEnabled;
    private boolean supportVision;
    private List<String> images;
    private String retrievedContext;

    public AgentContext toAgentContext(String userMessage) {
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.setUserMessage(userMessage);
        context.setToolsEnabled(toolsEnabled);
        context.setRagEnabled(ragEnabled);
        context.setImages(images);
        context.setVariable("chatExecutionContext", this);
        context.setVariable("modelCode", modelCode);
        context.setVariable("retrievedContext", retrievedContext);
        return context;
    }
}
