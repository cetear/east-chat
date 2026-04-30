package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("provider")
@Data
public class ProviderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("provider_code")
    private String providerCode;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key")
    private String apiKey;

    @TableField("enabled")
    private Integer enabled;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("last_fail_time")
    private LocalDateTime lastFailTime;

    @TableField("circuit_status")
    private String circuitStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
