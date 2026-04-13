package com.easychat.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    @Autowired(required = false)
    public ToolRegistry(List<Tool> toolList) {
        if (toolList != null) {
            for (Tool tool : toolList) {
                register(tool);
            }
        }
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
        log.info("Registered tool: {}", tool.name());
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> getAllTools() {
        return new HashMap<>(tools);
    }
}
