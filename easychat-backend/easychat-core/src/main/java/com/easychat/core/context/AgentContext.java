package com.easychat.core.context;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentContext {
    private Long sessionId;
    private String userMessage;
    private boolean toolsEnabled = false;
    private boolean ragEnabled = false;
    private int maxIterations = 5;
    private Map<String, Object> variables = new HashMap<>();

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }
}
