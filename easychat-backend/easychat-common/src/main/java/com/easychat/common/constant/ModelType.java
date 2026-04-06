package com.easychat.common.constant;

public enum ModelType {
    GPT_4("gpt-4"),
    GPT_4_TURBO("gpt-4-turbo"),
    GPT_3_5_TURBO("gpt-3.5-turbo"),
    CLAUDE_3_OPUS("claude-3-opus"),
    CLAUDE_3_SONNET("claude-3-sonnet");

    private final String value;

    ModelType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ModelType fromValue(String value) {
        for (ModelType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return GPT_3_5_TURBO; // default
    }
}
