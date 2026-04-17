package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@TableName("model_provider")
@Data
public class ModelProviderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("model_code")
    private String modelCode;

    @TableField("provider_code")
    private String providerCode;

    /** 优先级，数值越小越优先 */
    @TableField("priority")
    private Integer priority;

    /** 权重（用于未来按权重随机选择） */
    @TableField("weight")
    private Integer weight;

    @TableField("timeout_ms")
    private Integer timeoutMs;

    @TableField("max_retry")
    private Integer maxRetry;

    /** 熔断触发失败次数阈值 */
    @TableField("circuit_breaker_threshold")
    private Integer circuitBreakerThreshold;

    /** 熔断冷却时间（秒） */
    @TableField("circuit_breaker_window")
    private Integer circuitBreakerWindow;

    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;
}
