package com.easychat.api.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * @author Liny
 */
@Data
public class AddModelRequestDTO {
     /**模型名称**/
    private String modelCode;
    /**最大tokens**/
    private Integer maxTokens;
    /**默认配置**/
    private String defaultConfig;
    /**启用配置**/
    private Integer enabled;

    /** 渠道标识 **/
    private String providerCode;
    /** 优先级 **/
    private Integer priority;
    /** 权重 **/
    private Integer weight;
    /** 超时时间 **/
    private Integer timeoutMs;
    /** 最大重试次数 **/
    private Integer maxRetry;
}
