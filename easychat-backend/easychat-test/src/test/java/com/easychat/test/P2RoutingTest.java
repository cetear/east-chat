package com.easychat.test;
import com.easychat.core.router.*;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.llm.client.LLMClient;
import com.easychat.llm.provider.LLMProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class P2RoutingTest {
    @Test void halfOpenHasOneProbeAcrossReloadAndCancelReleasesIt() {
        var route=new ProviderWrapper(mock(LLMProvider.class),0,1,1,0);
        route.acquire().failure();var oldRequest=route.acquire();assertNotNull(oldRequest);assertNull(route.acquire());
        var refreshed=new ProviderWrapper(mock(LLMProvider.class),0,1,1,0);refreshed.inheritState(route);
        assertNull(refreshed.acquire());oldRequest.cancel();var probe=refreshed.acquire();assertNotNull(probe);probe.success();assertEquals("CLOSED",route.getCircuitStatus());
    }
    @Test void staleSuccessCannotCloseNewlyOpenedCircuit() {
        var route=new ProviderWrapper(mock(LLMProvider.class),0,1,1,30);
        var slow=route.acquire();route.acquire().failure();slow.success();assertEquals("OPEN",route.getCircuitStatus());assertNull(route.acquire());
    }
    @Test void retriesStopAtConfiguredBoundAndNeverRepeatAfterToken() {
        var registry=mock(ProviderRegistry.class);var provider=mock(LLMProvider.class);
        when(registry.isModelEnabled("m")).thenReturn(true);when(provider.getProviderCode()).thenReturn("p");
        when(registry.getProviders("m")).thenReturn(List.of(new ProviderWrapper(provider,0,1,10,30,2)));
        when(provider.streamChat(anyString(),any(),any())).thenReturn(Flux.error(new IllegalStateException("offline")));
        var router=new ModelRouter(registry,mock(LLMClient.class));var c=new ChatExecutionContext();c.setModelCode("m");
        assertThrows(IllegalStateException.class,()->router.streamChat("q",c).blockLast());verify(provider,times(3)).streamChat(anyString(),any(),any());
        clearInvocations(provider);when(provider.streamChat(anyString(),any(),any())).thenReturn(Flux.concat(Flux.just("partial"),Flux.error(new IllegalStateException("lost"))));
        assertThrows(IllegalStateException.class,()->router.streamChat("q",c).blockLast());verify(provider).streamChat(anyString(),any(),any());
    }
    @Test void weightedChoiceStaysWithinPriorityTier() {
        var registry=mock(ProviderRegistry.class);var a=mock(LLMProvider.class);var b=mock(LLMProvider.class);var lower=mock(LLMProvider.class);
        when(registry.isModelEnabled("m")).thenReturn(true);when(b.getProviderCode()).thenReturn("b");
        when(registry.getProviders("m")).thenReturn(List.of(new ProviderWrapper(a,0,1,3,30),new ProviderWrapper(b,0,3,3,30),new ProviderWrapper(lower,1,100,3,30)));
        when(b.chat(anyString(),any(),any())).thenReturn("chosen");var router=new ModelRouter(registry,mock(LLMClient.class));
        TestSupport.setField(router,"strategy","weighted");
        TestSupport.setField(router,"draw",(java.util.function.LongUnaryOperator)(bound->bound==4?1:0));
        var c=new ChatExecutionContext();c.setModelCode("m");assertEquals("chosen",router.chat("q",c));verifyNoInteractions(a,lower);
    }
    @Test void missingUsageNeverBecomesAZeroTokenClaim() {
        var c=new ChatExecutionContext();c.beginModelCall();c.recordUsage(new com.easychat.llm.client.ModelUsage(4,2,6,"STOP"));
        assertEquals(6L,c.usage().get("totalTokens"));assertEquals(true,c.usage().get("complete"));
        c.beginModelCall();assertEquals(false,c.usage().get("complete"));assertNull(c.usage().get("totalTokens"));
    }
}