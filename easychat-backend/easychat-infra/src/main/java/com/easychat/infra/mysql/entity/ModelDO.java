package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("model")
@Data
public class ModelDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_code")
    private String modelCode;

    @TableField("model_name")
    private String modelName;

    @TableField("model_type")
    private String modelType;

    @TableField("model_family")
    private String modelFamily;

    @TableField("context_window")
    private Integer contextWindow;

    @TableField("max_output_tokens")
    private Integer maxOutputTokens;

    @TableField("default_temperature")
    private BigDecimal defaultTemperature;

    @TableField("default_top_p")
    private BigDecimal defaultTopP;

    @TableField("default_config")
    private String defaultConfig;

    @TableField("support_vision")
    private Integer supportVision;

    @TableField("enabled")
    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
