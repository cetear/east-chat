package com.easychat.core.agent;

import com.easychat.core.context.AgentContext;
import lombok.Data;

@Data
public class AgentResult {
    private String response;
    private boolean success;
    private String errorMessage;

    public static AgentResult success(String response) {
        AgentResult result = new AgentResult();
        result.setSuccess(true);
        result.setResponse(response);
        return result;
    }

    public static AgentResult error(String errorMessage) {
        AgentResult result = new AgentResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
