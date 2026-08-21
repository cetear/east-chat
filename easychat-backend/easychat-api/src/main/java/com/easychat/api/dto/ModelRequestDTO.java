package com.easychat.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Liny
 */
@Data
public class ModelRequestDTO {
    /** 模型唯一编码 **/
    private String modelCode;
    /** 模型展示名称 **/
    private String modelName;
    /** 模型类型：chat/embedding/rerank/image/audio **/
    private String modelType;
    /** 模型系列 **/
    private String modelFamily;
    /** 上下文窗口 token 上限 **/
    private Integer contextWindow;
    /** 最大输出 token 上限 **/
    private Integer maxOutputTokens;
    /** 默认 temperature **/
    private BigDecimal defaultTemperature;
    /** 默认 top_p **/
    private BigDecimal defaultTopP;
    /** 默认扩展配置 **/
    private String defaultConfig;
    /** 是否支持图片输入：1=支持 0=不支持 **/
    private Integer supportVision;
    /** 启用配置 **/
    private Integer enabled;
}
