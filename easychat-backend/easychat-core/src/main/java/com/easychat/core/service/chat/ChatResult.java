package com.easychat.core.service.chat;

import lombok.Data;

@Data
public class ChatResult {
    private String sessionCode;
    private String content;
    private String sources;
    private String warning;
    private String providerCode;
    private String finishReason;
    private java.util.Map<String,Object> usage;
}
