package com.easychat.core.event;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ChatSessionRepository;
import com.easychat.infra.mysql.repository.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import lombok.RequiredArgsConstructor;
import java.util.*;
@Service @lombok.extern.slf4j.Slf4j @RequiredArgsConstructor
@ConditionalOnExpression("${easychat.events.enabled:true} && ${easychat.events.outbox.enabled:false}")
public class DurableChatEvents {
 private final OutboxStore store;
 private final ChatSessionRepository sessions;
 private final ApplicationEventPublisher publisher;
 private final ObjectMapper json;
 /** Participates in ChatTurnPersistence's successful-message transaction. */
 public void appendTurn(ChatMessage user,ChatMessage assistant) {
  if(!Integer.valueOf(com.easychat.common.constant.MessageStatus.SUCCESS).equals(assistant.getStatus()))return;
  String session=sessions.findById(user.getSessionId()).getSessionCode();
  for(var message:List.of(user,assistant)){var event=new ChatMessageCreatedEvent();event.setRole(message.getRole());fill(event,message,session);append(event);}
  var event=new ChatCompletedEvent();fill(event,assistant,session);event.setLatencyMs(assistant.getLatencyMs());append(event);
 }
 private void fill(ChatEvent event,ChatMessage message,String session){event.setSessionId(message.getSessionId());event.setSessionCode(session);event.setMessageId(message.getId());event.setModelCode(message.getModelCode());event.setProviderCode(message.getProviderCode());}
 private void append(ChatEvent event){try{store.append(event.getEventId(),event.getClass().getSimpleName(),json.writeValueAsString(event));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Event serialization failed",e);}}
 @Scheduled(fixedDelayString="${easychat.events.outbox.poll-ms:2000}")
 public void deliver(){
  for(String id:store.pending()){
   String token=UUID.randomUUID().toString();if(!store.claim(id,token))continue;
   try{
    var row=store.get(id);
    Class<? extends ChatEvent> type=switch(row.getEventType()){
     case "ChatMessageCreatedEvent" -> ChatMessageCreatedEvent.class;
     case "ChatCompletedEvent" -> ChatCompletedEvent.class;
     default -> throw new IllegalArgumentException("Unknown event type");
    };
    publisher.publishEvent(json.readValue(row.getPayload(),type));
    store.complete(id,token);log.info("Delivered chat event {} id={}",type.getSimpleName(),id);
   }catch(Exception e){log.warn("Chat event delivery will retry id={}",id);store.retry(id,token);}
  }
 }
}
