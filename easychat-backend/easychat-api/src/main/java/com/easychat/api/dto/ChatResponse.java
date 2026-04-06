package com.easychat.api.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String content;
    private String model;
    private String finishReason;
}
