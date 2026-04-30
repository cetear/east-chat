package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("chat_session_memory")
@Data
public class ChatSessionMemoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("summary")
    private String summary;

    @TableField("embedding")
    private byte[] embedding;

    @TableField("version")
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;
}