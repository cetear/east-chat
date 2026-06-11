package com.easychat.core.domain.chat;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private Long id;
    private String sessionCode;
    private String title;
    private String systemPrompt;
    private Integer maxRounds;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
