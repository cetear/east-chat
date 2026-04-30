package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName("chat_session")
@Data
public class ChatSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_code")
    private String sessionCode;

    @TableField("title")
    private String title;

    @TableField("model_code")
    private String modelCode;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("max_rounds")
    private Integer maxRounds;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}