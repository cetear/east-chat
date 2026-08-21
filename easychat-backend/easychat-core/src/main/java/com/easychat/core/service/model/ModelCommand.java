package com.easychat.core.service.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModelCommand {
    private String modelCode;
    private String modelName;
    private String modelType;
    private String modelFamily;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private BigDecimal defaultTemperature;
    private BigDecimal defaultTopP;
    private String defaultConfig;
    private Integer supportVision;
    private Integer enabled;
}
