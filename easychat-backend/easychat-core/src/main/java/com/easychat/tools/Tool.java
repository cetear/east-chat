package com.easychat.tools;

import java.util.Map;

public interface Tool {
    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述
     */
    String description();

    /**
     * 执行工具
     */
    String execute(Map<String, Object> args);

    default Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    default String execute(Map<String, Object> args, ToolContext context) {
        return execute(args);
    }
}
