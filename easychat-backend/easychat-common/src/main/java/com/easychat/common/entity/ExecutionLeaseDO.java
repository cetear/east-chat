package com.easychat.common.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("execution_lease")
public class ExecutionLeaseDO {
 @TableId(type=IdType.INPUT) private String lockKey;
 private String ownerToken;
 private LocalDateTime expiresAt;
}
