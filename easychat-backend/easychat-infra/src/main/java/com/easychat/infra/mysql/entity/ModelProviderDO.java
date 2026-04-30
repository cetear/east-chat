package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("model_provider")
@Data
public class ModelProviderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_code")
    private String modelCode;

    @TableField("provider_code")
    private String providerCode;

    @TableField("priority")
    private Integer priority;

    @TableField("weight")
    private Integer weight;

    @TableField("timeout_ms")
    private Integer timeoutMs;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField("enabled")
    private Integer enabled;

    @TableField("avg_latency_ms")
    private Integer avgLatencyMs;

    @TableField("success_rate")
    private Double successRate;

    @TableField("last_used_time")
    private LocalDateTime lastUsedTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
