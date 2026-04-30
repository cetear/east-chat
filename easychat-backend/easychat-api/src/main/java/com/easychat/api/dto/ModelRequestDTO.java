package com.easychat.api.dto;

import lombok.Data;

/**
 * @author Liny
 */
@Data
public class ModelRequestDTO {
     /**模型名称**/
    private String modelCode;
    /**最大tokens**/
    private Integer maxTokens;
    /**默认配置**/
    private String defaultConfig;
    /**启用配置**/
    private Integer enabled;
}
