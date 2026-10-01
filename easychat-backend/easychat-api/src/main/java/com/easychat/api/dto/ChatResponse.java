package com.easychat.api.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.easychat.core.service.chat.ChatResult;

import java.util.Map;

@Data
public class ChatResponse {
    private String content, sessionId, providerCode, finishReason;
    private Map<String, Object> usage;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sources;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String warning;

    public static ChatResponse from(ChatResult result) {
        var response = new ChatResponse();
        response.setContent(result.getContent());
        response.setSessionId(result.getSessionCode());
        response.setProviderCode(result.getProviderCode());
        response.setFinishReason(result.getFinishReason());
        response.setUsage(result.getUsage());
        response.setSources(result.getSources());
        response.setWarning(result.getWarning());
        return response;
    }
}
