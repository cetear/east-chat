package com.easychat.test;
import com.easychat.core.agent.*;
import com.easychat.core.context.*;
import com.easychat.core.port.ChatModelClient;
import com.easychat.core.router.*;
import com.easychat.llm.client.*;
import com.easychat.llm.provider.LLMProvider;
import com.easychat.tools.*;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class P1AgentRoutingTest {
    private ChatExecutionContext context() {
        var c = new ChatExecutionContext(); c.setModelCode("m"); c.setSessionId(7L); c.setDataset("alpha");
        c.setModelMessages(List.of(UserMessage.from("hello"))); return c;
    }
    @Test void realHelloToolAndNestedJsonReachObservation() {
        var model = mock(ChatModelClient.class);
        when(model.chat(anyString(),any())).thenReturn("Action: hello\nAction Input: {\"nested\":{\"list\":[1,true,\"a,b\"]}}", "Final Answer: done");
        var tool = spy(new com.easychat.tools.impl.HelloTool());
        var agent = new ReActAgent();
        TestSupport.setField(agent,"chatModelClient",model);
        TestSupport.setField(agent,"toolRegistry",new ToolRegistry(List.of(tool)));
        var c=context(); c.setToolsEnabled(true);
        var events=agent.streamRun(c.toAgentContext("hello")).collectList().block();
        assertTrue(events.stream().anyMatch(e -> e.getType()==AgentEvent.Type.OBSERVATION && e.getContent().contains("hello")));
        assertEquals("done", events.get(events.size()-1).getContent());
        verify(tool).execute(argThat(a -> a.get("nested") instanceof Map),eq(new ToolContext(7L,"alpha")));
        verify(model,times(2)).chat(anyString(),any());
    }
    @Test void malformedInputDoesNotExecuteAndLimitIsFailure() {
        var model=mock(ChatModelClient.class); when(model.chat(anyString(),any())).thenReturn("Action: hello\nAction Input: broken");
        var tool=spy(new com.easychat.tools.impl.HelloTool()); var agent=new ReActAgent();
        TestSupport.setField(agent,"chatModelClient",model); TestSupport.setField(agent,"toolRegistry",new ToolRegistry(List.of(tool)));
        var c=context(); c.setToolsEnabled(true); var a=c.toAgentContext("hi"); a.setMaxIterations(2);
        var error=assertThrows(IllegalStateException.class,()->agent.streamRun(a).blockLast());
        assertTrue(error.getMessage().contains("max_iterations")); verify(tool,never()).execute(anyMap(),any());
        verify(model,times(2)).chat(anyString(),any());
    }
    @Test void duplicateToolRejectedAndCancellationStopsNextTurn() {
        var tool=new com.easychat.tools.impl.HelloTool(); assertThrows(IllegalArgumentException.class,()->new ToolRegistry(List.of(tool,tool)));
        var model=mock(ChatModelClient.class); when(model.chat(anyString(),any())).thenReturn("Action: hello\nAction Input: {}");
        var agent=new ReActAgent(); TestSupport.setField(agent,"chatModelClient",model); TestSupport.setField(agent,"toolRegistry",new ToolRegistry(List.of(tool)));
        var c=context(); c.setToolsEnabled(true);
        agent.streamRun(c.toAgentContext("hi")).take(1).blockLast();
        verify(model,times(1)).chat(anyString(),any());
    }
    @Test void routeFallbackOnlyBeforeOutput() {
        var registry=mock(ProviderRegistry.class); var a=mock(LLMProvider.class); var b=mock(LLMProvider.class);
        when(a.getProviderCode()).thenReturn("a"); when(b.getProviderCode()).thenReturn("b");
        when(registry.isModelEnabled("m")).thenReturn(true);
        when(registry.getProviders("m")).thenReturn(List.of(new ProviderWrapper(a,1,1,5,60),new ProviderWrapper(b,2,1,5,60)));
        var router=new ModelRouter(registry,mock(LLMClient.class));
        when(a.streamMessages(anyList(),any())).thenReturn(Flux.error(new IllegalStateException("offline")));
        when(b.streamMessages(anyList(),any())).thenReturn(Flux.just("ok"));
        var c=context(); assertEquals(List.of("ok"),router.streamChat("",c).collectList().block()); assertEquals("b",c.getProviderCode());
        clearInvocations(b);
        when(a.streamMessages(anyList(),any())).thenReturn(Flux.concat(Flux.just("prefix"),Flux.error(new IllegalStateException("lost"))));
        var output=new ArrayList<String>();
        assertThrows(IllegalStateException.class,()->router.streamChat("",context()).doOnNext(output::add).blockLast());
        assertEquals(List.of("prefix"),output); verify(b,never()).streamMessages(anyList(),any());
    }
    @Test void disabledModelNeverFallsBackAndBreakerSurvivesReload() {
        var registry=mock(ProviderRegistry.class); var fallback=mock(LLMClient.class);
        assertThrows(IllegalArgumentException.class,()->new ModelRouter(registry,fallback).chat("",context())); verifyNoInteractions(fallback);
        var provider=mock(LLMProvider.class); var old=new ProviderWrapper(provider,1,1,1,60); old.recordFailure();
        var next=new ProviderWrapper(provider,1,1,1,60); next.inheritState(old); assertFalse(next.isAvailable()); next.recordSuccess(); assertTrue(next.isAvailable());
    }
}