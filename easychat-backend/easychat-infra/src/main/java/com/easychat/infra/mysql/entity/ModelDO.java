package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("model")
@Data
public class ModelDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_code")
    private String modelCode;

    @TableField("max_tokens")
    private Integer maxTokens;

    @TableField("default_config")
    private String defaultConfig;

    @TableField("enabled")
    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
