package com.easychat.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@TableName("chat_session")
@Data
public class ChatSessionDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("title")
    private String title;

    @TableField("model_type")
    private String modelType;

    @TableField("summary")
    private String summary;

    @TableField("max_rounds")
    private Integer maxRounds;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    /** 非数据库字段，由服务层加载后填充 */
    @TableField(exist = false)
    private List<ChatMessageDO> messages = new ArrayList<>();

    public void addMessage(ChatMessageDO message) {
        messages.add(message);
        message.setSessionId(this.id);
        this.updatedAt = System.currentTimeMillis();
    }
}
