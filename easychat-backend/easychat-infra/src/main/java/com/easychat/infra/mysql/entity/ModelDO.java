package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@TableName("model")
@Data
public class ModelDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统一模型标识，如 gpt-4o、deepseek-chat */
    @TableField("model_code")
    private String modelCode;

    @TableField("display_name")
    private String displayName;

    @TableField("max_tokens")
    private Integer maxTokens;

    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;
}
