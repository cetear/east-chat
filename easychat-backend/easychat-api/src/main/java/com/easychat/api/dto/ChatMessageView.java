package com.easychat.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageView {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String contentType;
    private Integer messageOrder;
    private String modelCode;
    private String providerCode;
    private Integer status;
    private String errorMsg;
    private String paramJson;
    private String finishReason;
    private Integer latencyMs;
    private Integer promptTokens, completionTokens, totalTokens;
    private LocalDateTime createdAt;

    public static ChatMessageView from(com.easychat.common.domain.chat.ChatMessage value) {
        var view = new ChatMessageView();
        view.setId(value.getId());
        view.setSessionId(value.getSessionId());
        view.setRole(value.getRole());
        view.setContent(value.getContent());
        view.setContentType(value.getContentType());
        view.setMessageOrder(value.getMessageOrder());
        view.setModelCode(value.getModelCode());
        view.setProviderCode(value.getProviderCode());
        view.setStatus(value.getStatus());
        view.setErrorMsg(value.getErrorMsg());
        view.setParamJson(value.getParamJson());
        view.setFinishReason(value.getFinishReason());
        view.setLatencyMs(value.getLatencyMs());
        view.setPromptTokens(value.getPromptTokens());
        view.setCompletionTokens(value.getCompletionTokens());
        view.setTotalTokens(value.getTotalTokens());
        view.setCreatedAt(value.getCreatedAt());
        return view;
    }
}
