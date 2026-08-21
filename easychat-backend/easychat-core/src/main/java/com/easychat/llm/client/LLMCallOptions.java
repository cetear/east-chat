package com.easychat.llm.client;

import lombok.Data;

import java.util.List;

@Data
public class LLMCallOptions {
    private String modelName;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private List<String> stop;
    private String responseFormat;
    private Integer seed;
    private Double presencePenalty;
    private Double frequencyPenalty;

    public static LLMCallOptions empty() {
        return new LLMCallOptions();
    }
}
