package com.easychat.infra.mysql.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.common.entity.ChatEventOutboxDO;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface ChatEventOutboxMapper extends BaseMapper<ChatEventOutboxDO> {
 @Insert("INSERT INTO chat_event_outbox(event_id,event_type,payload,status,attempts,available_at,created_at) VALUES (#{id},#{type},#{payload},'PENDING',0,NOW(3),NOW(3))") int append(@Param("id") String id,@Param("type") String type,@Param("payload") String payload);
 @Select("SELECT event_id FROM chat_event_outbox WHERE (status='PENDING' AND available_at<=NOW(3)) OR (status='INFLIGHT' AND lease_until<=NOW(3)) ORDER BY created_at LIMIT 50") List<String> pending();
 @Update("UPDATE chat_event_outbox SET status='INFLIGHT',owner_token=#{token},lease_until=TIMESTAMPADD(SECOND,60,NOW(3)),attempts=attempts+1 WHERE event_id=#{id} AND ((status='PENDING' AND available_at<=NOW(3)) OR (status='INFLIGHT' AND lease_until<=NOW(3)))") int claim(@Param("id") String id,@Param("token") String token);
 @Update("UPDATE chat_event_outbox SET status='DONE',delivered_at=NOW(3),last_error=NULL WHERE event_id=#{id} AND owner_token=#{token}") int complete(@Param("id") String id,@Param("token") String token);
 @Update("UPDATE chat_event_outbox SET status='PENDING',available_at=TIMESTAMPADD(SECOND,LEAST(300,POWER(2,LEAST(attempts,8))),NOW(3)),last_error='Delivery failed' WHERE event_id=#{id} AND owner_token=#{token}") int retry(@Param("id") String id,@Param("token") String token);
 @Select("SELECT status,COUNT(*) AS count,MAX(attempts) AS maxAttempts FROM chat_event_outbox GROUP BY status") List<Map<String,Object>> counts();
}
