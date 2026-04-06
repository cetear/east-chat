package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

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

    @TableField("message_order")
    private Integer messageOrder;

    @TableField("created_at")
    private Long createdAt;

    public ChatMessageDO() {
    }

    public ChatMessageDO(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
