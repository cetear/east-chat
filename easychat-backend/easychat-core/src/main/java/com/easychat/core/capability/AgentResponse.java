package com.easychat.core.capability;

import lombok.Data;

@Data
public class AgentResponse {
    private String content;
    private boolean success = true;
    private String errorMessage;
}
