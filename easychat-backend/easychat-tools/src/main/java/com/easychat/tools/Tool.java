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
}
