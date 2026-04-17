package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@TableName("provider")
@Data
public class ProviderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道标识，如 deepseek-official、openai-azure */
    @TableField("provider_code")
    private String providerCode;

    @TableField("base_url")
    private String baseUrl;

    /** 存储时建议加密，读取后解密使用 */
    @TableField("api_key")
    private String apiKey;

    @TableField("enabled")
    private Integer enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;
}
