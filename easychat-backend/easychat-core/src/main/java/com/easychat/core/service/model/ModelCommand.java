package com.easychat.core.service.model;

import lombok.Data;

@Data
public class ModelCommand {
    private String modelCode;
    private Integer maxTokens;
    private String defaultConfig;
    private Integer enabled;
}
