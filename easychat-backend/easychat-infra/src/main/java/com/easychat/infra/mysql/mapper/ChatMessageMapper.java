package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.common.domain.chat.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

 @org.apache.ibatis.annotations.Update("UPDATE chat_message m SET status=0,error_msg='Interrupted request',finish_reason='interrupted' WHERE status=2 AND NOT EXISTS (SELECT 1 FROM execution_lease l WHERE l.lock_key=CONCAT('session:',m.session_id) AND l.expires_at>NOW(3))")
 int recoverInterrupted();

}
