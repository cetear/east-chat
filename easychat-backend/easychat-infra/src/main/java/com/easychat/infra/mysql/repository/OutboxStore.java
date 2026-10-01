package com.easychat.infra.mysql.repository;
import com.easychat.infra.mysql.mapper.ChatEventOutboxMapper;
import com.easychat.common.entity.ChatEventOutboxDO;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.List;
@Repository @RequiredArgsConstructor
public class OutboxStore {
 private final ChatEventOutboxMapper mapper;
 public void append(String id,String type,String payload){mapper.append(id,type,payload);}
 public List<String> pending(){return mapper.pending();}
 public boolean claim(String id,String token){return mapper.claim(id,token)==1;}
 public ChatEventOutboxDO get(String id){return mapper.selectById(id);}
 public void complete(String id,String token){mapper.complete(id,token);}
 public void retry(String id,String token){mapper.retry(id,token);}
}
