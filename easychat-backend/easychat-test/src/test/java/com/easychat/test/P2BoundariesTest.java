package com.easychat.test;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.*;
import com.easychat.common.port.*;
import com.easychat.core.router.ProviderRegistry;
import com.easychat.core.service.model.*;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.port.ConversationMemory;
import com.easychat.memory.summary.impl.LlmSummaryService;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.rag.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P2BoundariesTest {
    @Test void retrievalSelectsOnlyPublishedGenerationsOwnedByCaller() {
        var type=com.easychat.common.entity.KnowledgeDocDO.class;
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"p2"),type);
        var mapper=mock(com.easychat.infra.mysql.mapper.KnowledgeDocMapper.class);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> query=invocation.getArgument(0);
            String sql=query.getSqlSegment();assertTrue(sql.contains("owner_id ="));assertTrue(sql.contains("dataset ="));assertTrue(sql.contains("status ="));
            assertTrue(query.getParamNameValuePairs().values().containsAll(List.of("Alice","alpha","INDEXED")));
            return List.of();
        });
        var es=new com.easychat.infra.es.EsClientWrapper();TestSupport.setField(es,"knowledgeDocMapper",mapper);
        assertEquals(List.of(),ReflectionTestUtils.invokeMethod(es,"activeGenerations","alpha","Alice"));
        verify(mapper).selectList(any());
    }

    @Test void imageOnlyToolLoopPreservesImageAndTrustedOwner() {
        var client=mock(ChatModelClient.class);var tool=spy(new com.easychat.tools.impl.HelloTool());
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(client.chat(anyString(),any())).thenAnswer(invocation -> {
            ChatExecutionContext context=invocation.getArgument(1);
            assertTrue(context.getModelMessages().stream().filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                .map(m -> (dev.langchain4j.data.message.UserMessage)m).flatMap(m -> m.contents().stream())
                .anyMatch(c -> c instanceof dev.langchain4j.data.message.ImageContent));
            return calls.getAndIncrement()==0?"Action: hello\nAction Input: {}":"Final Answer: hello";
        });
        var agent=new com.easychat.core.agent.ReActAgent();TestSupport.setField(agent,"chatModelClient",client);
        TestSupport.setField(agent,"toolRegistry",new com.easychat.tools.ToolRegistry(List.of(tool)));
        var context=new ChatExecutionContext();context.setToolsEnabled(true);context.setSessionId(1L);context.setUserId("Alice");context.setDataset("alpha");
        context.setModelMessages(List.of(dev.langchain4j.data.message.UserMessage.from(dev.langchain4j.data.message.ImageContent.from("https://example.com/image.png"))));
        agent.streamRun(context.toAgentContext("")).blockLast();assertEquals(2,calls.get());
        verify(tool).execute(anyMap(),eq(new com.easychat.tools.ToolContext(1L,"alpha","Alice")));
    }

    @Test void summaryCannotWriteAfterLeaseLossDuringModelCall() {
        var memory=mock(ConversationMemory.class); var model=mock(ChatModelClient.class);
        var summary=new LlmSummaryService();
        TestSupport.setField(summary,"conversationMemory",memory);
        TestSupport.setField(summary,"chatModelClient",model);
        TestSupport.setField(summary,"threshold",1); TestSupport.setField(summary,"keep",0);
        when(memory.countMessages(1L)).thenReturn(1);
        var message=new ChatMessage(); message.setId(2L); message.setRole("user"); message.setContent("fact");
        when(memory.unsummarized(1L,1)).thenReturn(List.of(message));
        when(model.chat(anyString(),any())).thenReturn("summary");
        var context=new ChatExecutionContext(); context.setSessionId(1L); context.setModelCode("m");
        context.setDurableWrite(write -> {throw new IllegalStateException("Lease lost");});
        assertThrows(IllegalStateException.class,()->summary.summarize(context));
        verify(model).chat(anyString(),any()); verify(memory,never()).saveSummary(anyLong(),anyString(),anyLong());
    }

    @Test void switchingToS3KeepsExistingLocalDocumentsReadable(@TempDir Path directory) throws Exception {
        var local=new LocalDocumentStorage(directory.toString());
        var objects=mock(DocumentStorage.class); var storage=new MigratingDocumentStorage(objects,local);
        String old=local.save("old fact".getBytes(),"alpha","old.txt");
        try(var file=storage.open(old)){assertEquals("old fact",Files.readString(file.path()));}
        when(objects.save(any(),eq("alpha"),eq("new.txt"))).thenReturn("s3://bucket/alpha/new.txt");
        assertEquals("s3://bucket/alpha/new.txt",storage.save(new byte[]{1},"alpha","new.txt"));
        storage.delete(old);assertFalse(Files.exists(Path.of(old)));verify(objects,never()).delete(old);
    }

    @Test void routeUpdatePreservesBindingAndRejectsInvalidRetryBeforeWrite() {
        var repository=mock(ModelCatalogRepository.class);var registry=mock(ProviderRegistry.class);
        var service=new ModelManageService();TestSupport.setField(service,"modelCatalogRepository",repository);
        TestSupport.setField(service,"registry",registry);
        var route=new ModelRoute(); route.setId(8L);route.setModelCode("m");route.setProviderCode("p");route.setTimeoutMs(5000);
        when(repository.findRoute("m","p")).thenReturn(route);
        var command=new ModelProviderCommand();command.setModelCode("untrusted-body");command.setWeight(7);command.setMaxRetry(2);
        service.updateModelProvider("p","m",command);
        assertEquals("m",route.getModelCode());assertEquals(5000,route.getTimeoutMs());assertEquals(7,route.getWeight());
        verify(repository).updateRoute(route);verify(registry).reload();
        clearInvocations(repository,registry);command.setMaxRetry(4);
        assertThrows(IllegalArgumentException.class,()->service.updateModelProvider("p","m",command));verifyNoInteractions(repository,registry);
    }
}
