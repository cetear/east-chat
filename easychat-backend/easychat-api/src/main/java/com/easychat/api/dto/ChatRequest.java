package com.easychat.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String sessionId;
    private List<MessageDTO> messages;
    private String model;
    private boolean toolsEnabled = false;
    private boolean ragEnabled = false;
}
