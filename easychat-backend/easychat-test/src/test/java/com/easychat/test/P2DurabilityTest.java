package com.easychat.test;
import com.easychat.infra.coordination.MysqlLeaseService;
import com.easychat.core.event.DurableChatEvents;
import com.easychat.common.domain.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class P2DurabilityTest {
    private DriverManagerDataSource source() {return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
    @Test void leaseContendersAndExpiredWorkersAreFenced() {
        var ds=source();var jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE execution_lease(lock_key VARCHAR(512) PRIMARY KEY,owner_token VARCHAR(64),expires_at TIMESTAMP)");jdbc.execute("CREATE TABLE work(id INT)");
        var firstService=new MysqlLeaseService(TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ExecutionLeaseMapper.class),TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ChatMessageMapper.class),manager);var secondService=new MysqlLeaseService(TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ExecutionLeaseMapper.class),TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ChatMessageMapper.class),manager);
        var first=firstService.acquire("doc:one");assertThrows(RuntimeException.class,()->secondService.acquire("doc:one"));
        assertThrows(IllegalStateException.class,()->first.guarded(()->{jdbc.update("INSERT INTO work VALUES(1)");throw new IllegalStateException("rollback");}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM work",Integer.class));
        jdbc.update("UPDATE execution_lease SET expires_at=TIMESTAMPADD(SECOND,-1,NOW())");var second=secondService.acquire("doc:one");
        assertThrows(IllegalStateException.class,()->first.guarded(()->{fail("Stale worker executed");return null;}));
        first.close();assertDoesNotThrow(second::check);second.close();
    }
    @Test void outboxRollsBackAndRetriesFailedDelivery() {
        var ds=source();var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE chat_session(id BIGINT PRIMARY KEY,session_code VARCHAR(64))");jdbc.update("INSERT INTO chat_session VALUES(1,'s')");
        jdbc.execute("CREATE TABLE chat_event_outbox(event_id VARCHAR(64) PRIMARY KEY,event_type VARCHAR(64),payload CLOB,status VARCHAR(16),attempts INT,available_at TIMESTAMP,owner_token VARCHAR(64),lease_until TIMESTAMP,created_at TIMESTAMP,delivered_at TIMESTAMP,last_error VARCHAR(512))");
        var publisher=mock(ApplicationEventPublisher.class);
        var sessions=mock(com.easychat.common.port.ChatSessionRepository.class);
        var session=new com.easychat.common.domain.chat.ChatSession();session.setSessionCode("s");when(sessions.findById(1L)).thenReturn(session);
        var outbox=new DurableChatEvents(new com.easychat.infra.mysql.repository.OutboxStore(TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ChatEventOutboxMapper.class)),sessions,publisher,TestSupport.JSON);
        var user=new ChatMessage();user.setId(1L);user.setSessionId(1L);user.setRole("user");user.setStatus(1);
        var answer=new ChatMessage();answer.setId(2L);answer.setSessionId(1L);answer.setRole("assistant");answer.setStatus(1);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{outbox.appendTurn(user,answer);throw new IllegalStateException("rollback");}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_event_outbox",Integer.class));
        tx.execute(status->{outbox.appendTurn(user,answer);return null;});assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM chat_event_outbox",Integer.class));
        doThrow(new IllegalStateException("listener down")).doNothing().when(publisher).publishEvent(any(Object.class));outbox.deliver();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM chat_event_outbox WHERE status='PENDING'",Integer.class));
        jdbc.update("UPDATE chat_event_outbox SET available_at=NOW()");outbox.deliver();assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM chat_event_outbox WHERE status='DONE'",Integer.class));
    }

    @Test void recoveryLeavesLiveChatsAloneAndFailsOnlyAbandonedTurns() {
        var ds=source();var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE execution_lease(lock_key VARCHAR(512) PRIMARY KEY,owner_token VARCHAR(64),expires_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE chat_message(session_id BIGINT,status INT,error_msg VARCHAR(128),finish_reason VARCHAR(32))");
        jdbc.update("INSERT INTO chat_message VALUES(1,2,NULL,NULL),(2,2,NULL,NULL),(3,1,NULL,NULL)");
        var leases=new MysqlLeaseService(TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ExecutionLeaseMapper.class),TestSupport.mappers(ds).getMapper(com.easychat.infra.mysql.mapper.ChatMessageMapper.class),new DataSourceTransactionManager(ds));
        try(var live=leases.acquire("session:1")) {
            leases.recoverInterruptedChats();
            assertEquals(2,jdbc.queryForObject("SELECT status FROM chat_message WHERE session_id=1",Integer.class));
            assertEquals("interrupted",jdbc.queryForObject("SELECT finish_reason FROM chat_message WHERE session_id=2",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT status FROM chat_message WHERE session_id=3",Integer.class));
        }
    }
}
