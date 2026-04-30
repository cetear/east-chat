package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("chat_message")
@Data
public class ChatMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("content_type")
    private String contentType;

    @TableField("message_order")
    private Integer messageOrder;

    @TableField("model_code")
    private String modelCode;

    @TableField("provider_code")
    private String providerCode;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("finish_reason")
    private String finishReason;

    @TableField("status")
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("param_json")
    private String paramJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}