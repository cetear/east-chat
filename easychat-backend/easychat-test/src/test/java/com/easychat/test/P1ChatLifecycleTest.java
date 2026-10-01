package com.easychat.test;
import com.easychat.core.agent.*;
import com.easychat.core.capability.AgentCapabilityRegistry;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.common.domain.chat.*;
import com.easychat.core.port.*;
import com.easychat.common.port.*;
import com.easychat.core.service.chat.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class P1ChatLifecycleTest {
    ChatExecutionService service; ReActAgent agent; ChatTurnPersistence persistence; ChatEventPublisher events;
    ChatExecutionContext context; SessionExecutionLock locks; ChatCommand command; AgentCapabilityRegistry capabilities;
    ChatMessage user,assistant; ChatSessionRepository sessions;
    @BeforeEach void setup() {
        service=new ChatExecutionService(); agent=mock(ReActAgent.class); persistence=mock(ChatTurnPersistence.class);
        events=mock(ChatEventPublisher.class); capabilities=mock(AgentCapabilityRegistry.class); locks=new SessionExecutionLock();
        var builder=mock(ChatContextBuilder.class); var session=new ChatSession(); session.setId(1L);session.setSessionCode("s");
        context=new ChatExecutionContext();context.setSessionId(1L);context.setSessionCode("s");
        command=new ChatCommand();command.setUserMessage("q");command.setModelCode("m");
        when(builder.resolveSession(command)).thenReturn(session);when(builder.buildContext(command,session)).thenReturn(context);
        when(persistence.begin(any(),any())).thenAnswer(i->{user=i.getArgument(0);assistant=i.getArgument(1);user.setId(1L);assistant.setId(2L);return new ChatMessage[]{user,assistant};});
        TestSupport.setField(service,"builder",builder);TestSupport.setField(service,"agent",agent);
        sessions=mock(ChatSessionRepository.class);TestSupport.setField(service,"persistence",persistence);TestSupport.setField(service,"sessions",sessions);
        TestSupport.setField(service,"capabilities",capabilities);TestSupport.setField(service,"events",events);TestSupport.setField(service,"locks",locks);
    }
    @Test void successPersistsBeforeHooksAndEvents() throws Exception {
        when(agent.streamRun(any())).thenReturn(Flux.just(AgentEvent.message("a"),AgentEvent.message("b")));
        var usecase=new ChatUseCase();TestSupport.setField(usecase,"execution",service);
        var result=usecase.chat(command);assertEquals("ab",result.getContent());assertEquals("s",result.getSessionCode());
        assertEquals(1,assistant.getStatus());assertEquals("ab",assistant.getContent());
        verify(sessions).update(argThat(s->s.getTitle()==null && s.getStatus()==null && s.getMaxRounds()==null && s.getUpdatedAt()!=null));
        var order=inOrder(persistence,capabilities,events);order.verify(persistence).finish(user,assistant);order.verify(capabilities).afterRun(any(),eq(context));order.verify(events,times(3)).publish(any());
        try(var ignored=locks.acquire(1L)) { assertNotNull(ignored); }
    }
    @Test void modelFailureSavesPartialOutputAsFailureAndNoSuccessEvents() throws Exception {
        when(agent.streamRun(any())).thenReturn(Flux.concat(Flux.just(AgentEvent.message("partial")),Flux.error(new IllegalStateException("offline"))));
        assertThrows(IllegalStateException.class,()->service.execute(command).blockLast());
        assertEquals(0,user.getStatus());assertEquals(0,assistant.getStatus());assertEquals("partial",assistant.getContent());assertEquals("error",assistant.getFinishReason());
        verifyNoInteractions(events); verify(capabilities,never()).afterRun(any(),any());
        try(var ignored=locks.acquire(1L)) { assertNotNull(ignored); }
    }
    @Test void cancellingStreamPersistsCancelledAndReleasesSession() throws Exception {
        var finished=new CountDownLatch(1);doAnswer(i->{finished.countDown();return null;}).when(persistence).finish(any(),any());
        when(agent.streamRun(any())).thenReturn(Flux.never());
        service.execute(command).take(1).blockLast();
        assertTrue(finished.await(5,TimeUnit.SECONDS));assertEquals(3,assistant.getStatus());assertTrue(context.isCancelled());verifyNoInteractions(events);
    }
    @Test void failedBeginReleasesLockAndNeverInvokesModel() throws Exception {
        doThrow(new IllegalStateException("db failure")).when(persistence).begin(any(),any());
        assertThrows(IllegalStateException.class,()->service.execute(command).blockLast());verifyNoInteractions(agent,events);
        try(var ignored=locks.acquire(1L)) { assertNotNull(ignored); }
    }
    @Test void sameSessionCannotExecuteConcurrently() throws Exception {
        try(var ignored=locks.acquire(1L)) {
            assertThrows(com.easychat.common.exception.BusinessException.class,()->service.execute(command).blockLast());
            verifyNoInteractions(persistence,agent);
        }
    }
    @Test void cancellationDuringSlowPersistenceStillCleansUp() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        doAnswer(i->{user=i.getArgument(0);assistant=i.getArgument(1);user.setId(1L);assistant.setId(2L);entered.countDown();
            boolean done=false;while(!done) {try {done=release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored) {}}
            return new ChatMessage[]{user,assistant};}).when(persistence).begin(any(),any());
        doAnswer(i->{finished.countDown();return null;}).when(persistence).finish(any(),any());
        var subscription=service.execute(command).subscribe();assertTrue(entered.await(5,TimeUnit.SECONDS));
        subscription.dispose();release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));
        assertEquals(3,assistant.getStatus());verifyNoInteractions(agent,events);
    }
}