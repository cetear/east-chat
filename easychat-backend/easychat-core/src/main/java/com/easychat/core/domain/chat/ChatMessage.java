package com.easychat.core.domain.chat;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
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
    private LocalDateTime createdAt;
}
