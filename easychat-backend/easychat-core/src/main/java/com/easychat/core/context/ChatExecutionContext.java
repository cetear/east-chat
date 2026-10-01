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
    private String dataset;
    private boolean supportVision;
    private List<String> images;
    private String retrievedContext;
    private String retrievedSourcesJson;
    private String retrievalWarning;
    private String systemPrompt;
    private int maxRounds = 10;
    private int maxContextChars = 16000;
    private boolean defaultModel;
    private volatile boolean cancelled;
    private Runnable leaseCheck = () -> {
    };

    public void checkLease() {
        leaseCheck.run();
    }

    private java.util.function.Consumer<Runnable> durableWrite = Runnable::run;

    public void writeGuarded(Runnable write) {
        durableWrite.accept(write);
    }

    private List<dev.langchain4j.data.message.ChatMessage> modelMessages = List.of();

    private int modelAttempts, reportedCalls;
    private long promptTokens, completionTokens, totalTokens;
    private boolean missingUsage;
    private String modelFinishReason;

    public synchronized void beginModelCall() {
        modelAttempts++;
    }

    public synchronized void recordUsage(com.easychat.llm.client.ModelUsage usage) {
        reportedCalls++;
        if (usage != null && usage.finishReason() != null)
            modelFinishReason = usage.finishReason().toLowerCase(java.util.Locale.ROOT);
        if (usage == null || usage.promptTokens() == null || usage.completionTokens() == null || usage.totalTokens() == null) {
            missingUsage = true;
            return;
        }
        promptTokens += usage.promptTokens();
        completionTokens += usage.completionTokens();
        totalTokens += usage.totalTokens();
    }

    public synchronized java.util.Map<String, Object> usage() {
        boolean complete = modelAttempts > 0 && reportedCalls == modelAttempts && !missingUsage;
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("modelFinishReason", modelFinishReason);
        result.put("complete", complete);
        result.put("attempts", modelAttempts);
        result.put("reportedCalls", reportedCalls);
        result.put("promptTokens", complete ? promptTokens : null);
        result.put("completionTokens", complete ? completionTokens : null);
        result.put("totalTokens", complete ? totalTokens : null);
        return result;
    }

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
