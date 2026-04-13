package com.easychat.llm.prompt;

import java.util.HashMap;
import java.util.Map;

public class PromptTemplate {
    private String template;

    public PromptTemplate(String template) {
        this.template = template;
    }

    public String format(Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public static PromptTemplate create(String template) {
        return new PromptTemplate(template);
    }
}
