package com.easychat.core.agent;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentEvent {

    public enum Type {
        THOUGHT, ACTION, OBSERVATION, MESSAGE, ERROR, FINISH
    }

    private Type type;
    private String content;
    private String toolName;
    private Map<String, Object> toolInput;

    private AgentEvent(Type type) {
        this.type = type;
    }

    public static AgentEvent thought(String content) {
        AgentEvent event = new AgentEvent(Type.THOUGHT);
        event.setContent(content);
        return event;
    }

    public static AgentEvent action(String toolName, Map<String, Object> toolInput) {
        AgentEvent event = new AgentEvent(Type.ACTION);
        event.setToolName(toolName);
        event.setToolInput(toolInput != null ? toolInput : new HashMap<>());
        return event;
    }

    public static AgentEvent observation(String content) {
        AgentEvent event = new AgentEvent(Type.OBSERVATION);
        event.setContent(content);
        return event;
    }

    public static AgentEvent message(String token) {
        AgentEvent event = new AgentEvent(Type.MESSAGE);
        event.setContent(token);
        return event;
    }

    public static AgentEvent error(String message) {
        AgentEvent event = new AgentEvent(Type.ERROR);
        event.setContent(message);
        return event;
    }

    public static AgentEvent finish(String reason) {
        AgentEvent event = new AgentEvent(Type.FINISH);
        event.setContent(reason);
        return event;
    }

    /**
     * 转换为 JSON 字符串（用于 SSE data 字段）
     */
    public String toJsonData() {
        StringBuilder sb = new StringBuilder("{");
        switch (type) {
            case THOUGHT, OBSERVATION -> sb.append("\"content\":\"").append(escapeJson(content)).append("\"");
            case ACTION -> {
                sb.append("\"tool\":\"").append(escapeJson(toolName)).append("\"");
                sb.append(",\"input\":").append(mapToJson(toolInput));
            }
            case MESSAGE, ERROR, FINISH -> sb.append("\"content\":\"").append(escapeJson(content)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(escapeJson((String) val)).append("\"");
            } else if (val == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(val.toString())).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
