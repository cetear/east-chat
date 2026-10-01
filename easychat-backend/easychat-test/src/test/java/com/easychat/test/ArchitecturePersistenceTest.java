package com.easychat.test;
import com.easychat.infra.mysql.mapper.*;
import com.easychat.infra.mysql.repository.*;
import com.easychat.core.event.DurableChatEvents;
import com.easychat.core.service.chat.ChatTurnPersistence;
import com.easychat.common.domain.chat.*;
import com.easychat.common.port.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArchitecturePersistenceTest {
 private DriverManagerDataSource source(){return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
 private void outbox(JdbcTemplate db){db.execute("CREATE TABLE chat_event_outbox(event_id VARCHAR(64) PRIMARY KEY,event_type VARCHAR(64),payload CLOB,status VARCHAR(16),attempts INT,available_at TIMESTAMP,owner_token VARCHAR(64),lease_until TIMESTAMP,created_at TIMESTAMP,delivered_at TIMESTAMP,last_error VARCHAR(512))");}
 @Test void messageAndOutboxShareTheAnnotatedTransaction() {
  var ds=source();var db=new JdbcTemplate(ds);outbox(db);
  db.execute("CREATE TABLE chat_message(id BIGINT PRIMARY KEY,session_id BIGINT,role VARCHAR(32),content CLOB,content_type VARCHAR(32),message_order INT,model_code VARCHAR(64),provider_code VARCHAR(64),prompt_tokens INT,completion_tokens INT,total_tokens INT,finish_reason VARCHAR(64),status INT,error_msg VARCHAR(512),latency_ms INT,param_json CLOB,created_at TIMESTAMP)");
  db.update("INSERT INTO chat_message(id,status) VALUES(1,2),(2,2)");
  var sql=TestSupport.mappers(ds);var repository=new ChatMessageMysqlRepository();TestSupport.setField(repository,"chatMessageMapper",sql.getMapper(ChatMessageMapper.class));
  var sessions=mock(ChatSessionRepository.class);var session=new ChatSession();session.setSessionCode("s");when(sessions.findById(1L)).thenReturn(session);
  var store=new OutboxStore(sql.getMapper(ChatEventOutboxMapper.class));
  var events=spy(new DurableChatEvents(store,sessions,mock(org.springframework.context.ApplicationEventPublisher.class),TestSupport.JSON));
  var target=new ChatTurnPersistence();TestSupport.setField(target,"messages",repository);TestSupport.setField(target,"outbox",events);
  var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
  var service=(ChatTurnPersistence)factory.getProxy();
  var user=new ChatMessage();user.setId(1L);user.setSessionId(1L);user.setStatus(1);user.setRole("user");
  var answer=new ChatMessage();answer.setId(2L);answer.setSessionId(1L);answer.setStatus(1);answer.setRole("assistant");
  doAnswer(i->{i.callRealMethod();throw new IllegalStateException("rollback after event insert");}).when(events).appendTurn(any(),any());
  assertThrows(IllegalStateException.class,()->service.finish(user,answer));
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM chat_message WHERE status=2",Integer.class));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM chat_event_outbox",Integer.class));
  doCallRealMethod().when(events).appendTurn(any(),any());service.finish(user,answer);
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM chat_message WHERE status=1",Integer.class));assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM chat_event_outbox",Integer.class));
 }
 @Test void twoClaimantsExpiredReclaimAndStaleAcknowledgements() throws Exception {
  var ds=source();var db=new JdbcTemplate(ds);outbox(db);var mapper=TestSupport.mappers(ds).getMapper(ChatEventOutboxMapper.class);mapper.append("event","type","{}");
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try {
   var a=pool.submit(()->{start.await();return mapper.claim("event","a");});var b=pool.submit(()->{start.await();return mapper.claim("event","b");});start.countDown();assertEquals(1,a.get()+b.get());
   String old=mapper.selectById("event").getOwnerToken();db.update("UPDATE chat_event_outbox SET lease_until=TIMESTAMPADD(SECOND,-1,NOW())");
   assertEquals(List.of("event"),mapper.pending());assertEquals(1,mapper.claim("event","new"));assertEquals(0,mapper.complete("event",old));assertEquals(0,mapper.retry("event",old));
   assertEquals(1,mapper.retry("event","new"));assertTrue(mapper.pending().isEmpty());
   db.update("UPDATE chat_event_outbox SET available_at=NOW()");assertEquals(1,mapper.claim("event","last"));assertEquals(1,mapper.complete("event","last"));assertEquals("event",mapper.selectById("event").getEventId());
  }finally{pool.shutdownNow();}
 }
 @Test void documentRecoveryHonorsLiveLeasesAndStatusDomains(){
  var ds=source();var db=new JdbcTemplate(ds);db.execute("CREATE TABLE execution_lease(lock_key VARCHAR(512) PRIMARY KEY,owner_token VARCHAR(64),expires_at TIMESTAMP)");db.execute("CREATE TABLE knowledge_doc(doc_code VARCHAR(64),status VARCHAR(32))");
  db.update("INSERT INTO knowledge_doc VALUES('live','INDEXING'),('expired','INDEXING'),('delete','DELETING'),('ready','INDEXED'),('failed','FAILED')");db.update("INSERT INTO execution_lease VALUES('doc:live','x',TIMESTAMPADD(SECOND,60,NOW())),('doc:expired','y',TIMESTAMPADD(SECOND,-1,NOW()))");
  var mapper=TestSupport.mappers(ds).getMapper(KnowledgeDocMapper.class);assertEquals(2,mapper.recoverExpired());
  assertEquals(com.easychat.common.constant.DocumentStatus.INDEXING,db.queryForObject("SELECT status FROM knowledge_doc WHERE doc_code='live'",String.class));assertEquals(com.easychat.common.constant.DocumentStatus.PENDING,db.queryForObject("SELECT status FROM knowledge_doc WHERE doc_code='expired'",String.class));assertEquals(com.easychat.common.constant.DocumentStatus.DELETE_FAILED,db.queryForObject("SELECT status FROM knowledge_doc WHERE doc_code='delete'",String.class));assertEquals(0,mapper.recoverExpired());
 }
 @Test void guardedWriteHoldsTheLeaseRowUntilCommit() throws Exception {
  var ds=source();var db=new JdbcTemplate(ds);db.execute("CREATE TABLE execution_lease(lock_key VARCHAR(512) PRIMARY KEY,owner_token VARCHAR(64),expires_at TIMESTAMP)");var sql=TestSupport.mappers(ds);var mapper=sql.getMapper(ExecutionLeaseMapper.class);
  var service=new com.easychat.infra.coordination.MysqlLeaseService(mapper,sql.getMapper(ChatMessageMapper.class),new DataSourceTransactionManager(ds));
  try(var lease=service.acquire("doc:locked")) {
   var pool=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var released=new CountDownLatch(1);
   try {
    var writer=pool.submit(()->lease.guarded(()->{entered.countDown();try{assertTrue(released.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));assertTrue(entered.await(5,TimeUnit.SECONDS));
    var blocked=CompletableFuture.supplyAsync(()->db.update("UPDATE execution_lease SET owner_token='next' WHERE lock_key='doc:locked'"));
    assertThrows(TimeoutException.class,()->blocked.get(200,TimeUnit.MILLISECONDS));released.countDown();writer.get(5,TimeUnit.SECONDS);assertEquals(1,blocked.get(5,TimeUnit.SECONDS));
   }finally{released.countDown();pool.shutdownNow();}
  }
 }
}
