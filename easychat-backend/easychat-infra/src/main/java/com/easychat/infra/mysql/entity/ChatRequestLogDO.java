package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("chat_request_log")
@Data
public class ChatRequestLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_id")
    private Long messageId;

    @TableField("model_code")
    private String modelCode;

    @TableField("provider_code")
    private String providerCode;

    @TableField("request_json")
    private String requestJson;

    @TableField("response_json")
    private String responseJson;

    @TableField("status")
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}