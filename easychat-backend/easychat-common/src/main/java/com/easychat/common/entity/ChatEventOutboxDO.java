package com.easychat.common.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("chat_event_outbox")
public class ChatEventOutboxDO {
 @TableId(type=IdType.INPUT) private String eventId;
 private String eventType,payload,status,ownerToken,lastError;
 private Integer attempts;
 private LocalDateTime availableAt,leaseUntil,createdAt,deliveredAt;
}
