package com.easychat.test;

import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.port.ModelCatalogRepository;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.router.*;
import com.easychat.core.service.model.*;
import com.easychat.llm.client.LLMClient;
import com.easychat.llm.provider.LLMProvider;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatModelConfigurationTest {
    @Test void aliasRoutesByInternalCodeAndUsesUpstreamNameForSyncAndStream() {
        var registry = mock(ProviderRegistry.class);
        var provider = mock(LLMProvider.class);
        when(registry.isModelEnabled("ds_1")).thenReturn(true);
        when(provider.getProviderCode()).thenReturn("DS_Ofiice");
        when(registry.getProviders("ds_1")).thenReturn(List.of(new ProviderWrapper(provider,0,1,3,30)));
        when(provider.chatMessages(anyList(),any())).thenReturn("OK");
        when(provider.streamMessages(anyList(),any())).thenReturn(Flux.just("OK"));
        var router = new ModelRouter(registry,mock(LLMClient.class));
        TestSupport.setField(router,"strategy","priority");
        var context = new ChatExecutionContext();
        context.setModelCode("ds_1");
        context.setModelMessages(List.of(UserMessage.from("Reply OK")));
        context.setDefaultConfig("{\"model_name\":\"deepseek-chat\"}");
        assertEquals("OK",router.chat("",context));
        assertEquals("OK",router.streamChat("",context).blockLast());
        verify(provider).chatMessages(anyList(),argThat(o -> "deepseek-chat".equals(o.getModelName())));
        verify(provider).streamMessages(anyList(),argThat(o -> "deepseek-chat".equals(o.getModelName())));
        assertEquals("ds_1",context.getModelCode());
        assertEquals("DS_Ofiice",context.getProviderCode());
        context.setDefaultConfig("{}");
        router.chat("",context);
        verify(provider).chatMessages(anyList(),argThat(o -> "ds_1".equals(o.getModelName())));
    }

    @Test void invalidCreateBudgetIsRejectedBeforeInsert() {
        var repository = mock(ModelCatalogRepository.class);
        var registry = mock(ProviderRegistry.class);
        var service = service(repository,registry);
        var command = new ModelCommand();
        command.setModelCode("ds_1");command.setContextWindow(1024);command.setMaxOutputTokens(1024);
        assertThrows(IllegalArgumentException.class,() -> service.addModel(command));
        verify(repository,never()).insertModel(any());verifyNoInteractions(registry);
    }

    @Test void partialUpdateChecksEffectiveBudgetAndAcceptsRepair() {
        var repository = mock(ModelCatalogRepository.class);
        var registry = mock(ProviderRegistry.class);
        var service = service(repository,registry);
        var existing = new ModelDefinition();existing.setContextWindow(8192);existing.setMaxOutputTokens(1024);
        when(repository.findModelByCode("ds_1")).thenReturn(existing);
        var command = new ModelCommand();command.setModelCode("ds_1");command.setContextWindow(1024);
        assertThrows(IllegalArgumentException.class,() -> service.updateModel(command));
        verify(repository,never()).updateModel(any());verifyNoInteractions(registry);
        command.setContextWindow(8192);command.setDefaultConfig("{\"model_name\":\"deepseek-chat\"}");
        service.updateModel(command);verify(repository).updateModel(existing);verify(registry).reload();
    }

    @Test void invalidAliasIsRejectedBeforeSave() {
        for (String value : List.of("null","123","\"  \"")) {
            var repository = mock(ModelCatalogRepository.class);
            var service = service(repository,mock(ProviderRegistry.class));
            var command = new ModelCommand();command.setModelCode("ds_1");
            command.setDefaultConfig("{\"model_name\":" + value + "}");
            assertThrows(IllegalArgumentException.class,() -> service.addModel(command));
            verify(repository,never()).insertModel(any());
        }
    }

    private ModelManageService service(ModelCatalogRepository repository,ProviderRegistry registry) {
        var service = new ModelManageService();
        TestSupport.setField(service,"modelCatalogRepository",repository);
        TestSupport.setField(service,"registry",registry);
        return service;
    }
}
