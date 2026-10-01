package com.easychat.test;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.core.port.*;
import com.easychat.common.port.*;
import com.easychat.core.service.chat.*;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ConversationMemory;
import com.easychat.memory.summary.impl.LlmSummaryService;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class P1ContextSummaryTest {
    ChatContextBuilder builder; ConversationMemory memory; ModelDefinition model; ChatSession session; ChatCommand command;
    @BeforeEach void setup() {
        builder=new ChatContextBuilder();memory=mock(ConversationMemory.class);var catalog=mock(ModelCatalogRepository.class);
        model=new ModelDefinition();model.setEnabled(1);model.setSupportVision(1);when(catalog.findModelByCode("m")).thenReturn(model);
        TestSupport.setField(builder,"memory",memory);TestSupport.setField(builder,"modelCatalogRepository",catalog);
        session=new ChatSession();session.setId(1L);session.setStatus(1);session.setMaxRounds(2);session.setSystemPrompt("be precise");
        command=new ChatCommand();command.setModelCode("m");command.setUserMessage("current");
        when(memory.getRecentMessages(1L,2)).thenReturn(List.of());
    }
    @Test void systemRoundsHistoryAndCurrentOnce() {
        var old=new ChatMessage();old.setRole("assistant");old.setContent("previous");
        var oldUser=new ChatMessage();oldUser.setRole("user");oldUser.setContent("old question");
        when(memory.getRecentMessages(1L,2)).thenReturn(List.of(oldUser,old));when(memory.getSummary(1L)).thenReturn("old facts");
        var c=builder.buildContext(command,session);builder.prepareMessages(command,c);
        assertEquals(4,c.getModelMessages().size());assertTrue(((SystemMessage)c.getModelMessages().get(0)).text().contains("be precise"));
        assertTrue(((SystemMessage)c.getModelMessages().get(0)).text().contains("old facts"));
        assertEquals("previous",((AiMessage)c.getModelMessages().get(2)).text());
        assertEquals("current",((TextContent)((UserMessage)c.getModelMessages().get(3)).contents().get(0)).text());verify(memory).getRecentMessages(1L,2);
    }
    @Test void currentAndStoredImagesBecomeStructuredMessages() {
        command.setImages(List.of("https://example.com/image.png"));var c=builder.buildContext(command,session);
        var old=new ChatMessage();old.setRole("user");old.setContent("old");old.setParamJson("{\"images\":[\"https://example.com/old.png\"]}");
        when(memory.getRecentMessages(1L,2)).thenReturn(List.of(old));builder.prepareMessages(command,c);
        assertInstanceOf(ImageContent.class,((UserMessage)c.getModelMessages().get(1)).contents().get(1));
        assertInstanceOf(ImageContent.class,((UserMessage)c.getModelMessages().get(2)).contents().get(1));
        command.setToolsEnabled(true);assertDoesNotThrow(()->builder.buildContext(command,session));
    }
    @Test void disabledModelClosedSessionAndOverBudgetFail() {
        model.setEnabled(0);assertThrows(RuntimeException.class,()->builder.buildContext(command,session));model.setEnabled(1);
        session.setStatus(0);assertThrows(RuntimeException.class,()->builder.buildContext(command,session));session.setStatus(1);
        var c=builder.buildContext(command,session);c.setMaxContextChars(2);assertThrows(IllegalArgumentException.class,()->builder.prepareMessages(command,c));
    }
    @Test void summaryMergesOldFactsAndAdvancesOnlyProcessedCursor() {
        var summary=new LlmSummaryService();var client=mock(ChatModelClient.class);
        TestSupport.setField(summary,"conversationMemory",memory);TestSupport.setField(summary,"chatModelClient",client);
        TestSupport.setField(summary,"threshold",2);TestSupport.setField(summary,"keep",2);
        when(memory.countMessages(1L)).thenReturn(4,2);when(memory.getSummary(1L)).thenReturn("old facts");
        var first=new ChatMessage();first.setId(10L);first.setRole("user");first.setContent("new facts");
        var second=new ChatMessage();second.setId(11L);second.setRole("assistant");second.setContent("x".repeat(5000));
        when(memory.unsummarized(1L,2)).thenReturn(List.of(first,second));when(client.chat(anyString(),any())).thenReturn("merged");
        var c=builder.buildContext(command,session);c.setMaxContextChars(1024);summary.summarize(c);summary.summarize(c);
        verify(client).chat(argThat(p->p.contains("old facts")&&p.contains("new facts")),argThat(ctx->"m".equals(ctx.getModelCode())));
        verify(memory).saveSummary(1L,"merged",10L);
    }
}