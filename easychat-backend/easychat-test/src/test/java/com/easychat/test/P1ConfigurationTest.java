package com.easychat.test;
import com.easychat.core.event.*;
import com.easychat.core.port.ChatEventPublisher;
import com.easychat.core.capability.*;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.api.config.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P1ConfigurationTest {
    @Test void esGroupCanBeDisabledWithoutEsOrEmbeddingDependencies() {
        new ApplicationContextRunner().withPropertyValues("easychat.es.enabled=false")
            .withUserConfiguration(com.easychat.infra.es.EsConfig.class,com.easychat.infra.es.EsClientWrapper.class,
                com.easychat.infra.es.EsIndexManager.class,com.easychat.rag.dataset.KnowledgeService.class,
                com.easychat.rag.pipeline.DocumentIngestionService.class,com.easychat.rag.pipeline.IngestionRecoveryRunner.class,
                com.easychat.rag.config.RagAsyncConfig.class,com.easychat.rag.retriever.impl.EsRetriever.class,
                com.easychat.rag.retriever.impl.HybridEsRetriever.class,com.easychat.tools.impl.EsQueryTool.class,
                com.easychat.api.controller.EsController.class,com.easychat.api.controller.KnowledgeController.class)
            .run(c->{assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(co.elastic.clients.elasticsearch.ElasticsearchClient.class).size());assertEquals(0,c.getBeansOfType(com.easychat.rag.pipeline.DocumentIngestionService.class).size());});
    }
    @Configuration(proxyBeanMethods=false) static class CustomEvents {
        @Bean ChatEventPublisher customPublisher() { return event -> {}; }
    }
    @Test void eventPublisherWorksAndDefaultBacksOff() {
        new ApplicationContextRunner().withUserConfiguration(CustomEvents.class,ChatEventConfiguration.class)
            .run(c->{assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(ChatEventPublisher.class).size());assertTrue(c.containsBean("customPublisher"));});
        new ApplicationContextRunner().withUserConfiguration(ChatEventConfiguration.class).run(c->{
            var received=new ArrayList<Object>();c.getSourceApplicationContext().addApplicationListener(received::add);
            c.getBean(ChatEventPublisher.class).publish(new ChatCompletedEvent());
            assertTrue(received.stream().anyMatch(e->e instanceof org.springframework.context.PayloadApplicationEvent<?> p && p.getPayload() instanceof ChatCompletedEvent));
        });
    }
    @Test void capabilitySupportAndOrderAndErrorStatus() {
        var a=mock(AgentCapability.class);var b=mock(AgentCapability.class);var skipped=mock(AgentCapability.class);var c=new ChatExecutionContext();
        when(a.supports(c)).thenReturn(true);when(b.supports(c)).thenReturn(true);
        var registry=new AgentCapabilityRegistry(List.of(a,skipped,b));var request=new AgentRequest();var response=new AgentResponse();
        registry.beforeRun(request,c);registry.afterRun(response,c);
        var order=inOrder(a,b);order.verify(a).beforeRun(request,c);order.verify(b).beforeRun(request,c);order.verify(a).afterRun(response,c);order.verify(b).afterRun(response,c);
        verify(skipped,never()).beforeRun(request,c);var handler=new ApiExceptionHandler();
        assertEquals(400,handler.invalid(new IllegalArgumentException("bad")).getStatusCode().value());
        assertEquals(500,handler.failure(new IllegalStateException("secret")).getStatusCode().value());
        assertFalse(handler.failure(new IllegalStateException("secret")).getBody().toString().contains("secret"));
    }
}