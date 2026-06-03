package com.easychat.core.capability;

import lombok.Data;

@Data
public class AgentRequest {
    private Long sessionId;
    private String userMessage;
    private boolean toolsEnabled;
    private boolean ragEnabled;
}
