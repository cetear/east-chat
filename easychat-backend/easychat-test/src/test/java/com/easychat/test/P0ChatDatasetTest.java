package com.easychat.test;

import com.easychat.api.controller.ChatController;
import com.easychat.api.dto.ChatRequest;
import com.easychat.api.dto.MessageDTO;
import com.easychat.core.capability.AgentRequest;
import com.easychat.core.capability.RagRetrievalCapability;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.api.facade.AgentFacade;
import com.easychat.common.port.ModelCatalogRepository;
import com.easychat.core.service.chat.*;
import com.easychat.rag.retriever.RetrievalRequest;
import com.easychat.rag.retriever.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class P0ChatDatasetTest {
    private ChatController controller;
    private ChatUseCase sync;
    private StreamChatUseCase streaming;
    private SessionUseCase sessions;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        controller = new ChatController();
        AgentFacade facade = new AgentFacade();
        sync = mock(ChatUseCase.class);
        streaming = mock(StreamChatUseCase.class);
        sessions = mock(SessionUseCase.class);
        session = new ChatSession();
        session.setId(1L); session.setStatus(1);
        session.setSessionCode("session");
        when(sessions.getSession("session")).thenReturn(session);
        when(sessions.getSessionById(1L)).thenReturn(session);
        when(sync.chat(any())).thenReturn(new ChatResult());
        when(streaming.streamChat(any())).thenReturn(Flux.empty());
        TestSupport.setField(facade, "chatUseCase", sync);
        TestSupport.setField(facade, "streamChatUseCase", streaming);
        TestSupport.setField(facade, "sessionUseCase", sessions);
        TestSupport.setField(controller, "agentFacade", facade);
    }

    private ChatRequest request(String dataset) {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session");
        request.setModel("model");
        request.setRagEnabled(true);
        request.setDataset(dataset);
        MessageDTO message = new MessageDTO();
        message.setRole("user");
        message.setContent("question");
        request.setMessages(List.of(message));
        return request;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void datasetSurvivesControllerFacadeContextAndCapability(boolean stream) {
        for (String input : new String[]{null, " ", " alpha "}) {
            String expected = input == null || input.isBlank() ? "default" : "alpha";
            clearInvocations(sync, streaming);
            if (stream) controller.streamChat(request(input));
            else assertEquals(200, controller.chat(request(input)).getStatusCode().value());
            ArgumentCaptor<ChatCommand> command = ArgumentCaptor.forClass(ChatCommand.class);
            if (stream) verify(streaming).streamChat(command.capture());
            else verify(sync).chat(command.capture());

            ChatContextBuilder builder = new ChatContextBuilder();
                        ModelCatalogRepository catalog = mock(ModelCatalogRepository.class);
            var model = new com.easychat.common.domain.model.ModelDefinition(); model.setEnabled(1);
            when(catalog.findModelByCode("model")).thenReturn(model);
            TestSupport.setField(builder, "modelCatalogRepository", catalog);
            var context = builder.buildContext(command.getValue(), session);
            Retriever retriever = mock(Retriever.class);
            when(retriever.retrieve(any(RetrievalRequest.class))).thenReturn(new com.easychat.rag.retriever.RetrievalResult(List.of(), null));
            RagRetrievalCapability capability = new RagRetrievalCapability();
            TestSupport.setField(capability, "retriever", retriever);
            TestSupport.setField(capability, "topK", 3);
            AgentRequest agentRequest = builder.buildAgentRequest(command.getValue(), session);
            capability.beforeRun(agentRequest, context);
            verify(retriever).retrieve(new RetrievalRequest("question", 3, expected));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidDatasetIsRejectedBeforeSessionOrModelWork(boolean stream) {
        if (stream) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> controller.streamChat(request("../outside")));
            assertEquals(400, error.getStatusCode().value());
        } else {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.chat(request("../outside"))).getStatusCode().value());
        }
        verifyNoInteractions(sessions, sync, streaming);
    }
}
