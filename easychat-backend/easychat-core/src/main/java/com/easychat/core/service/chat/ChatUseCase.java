package com.easychat.core.service.chat;

import com.easychat.core.agent.ReActAgent;
import com.easychat.core.capability.AgentCapabilityRegistry;
import com.easychat.core.capability.AgentRequest;
import com.easychat.core.capability.AgentResponse;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.domain.chat.ChatMessage;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.domain.model.ModelDefinition;
import com.easychat.core.event.ChatCompletedEvent;
import com.easychat.core.event.ChatMessageCreatedEvent;
import com.easychat.core.port.ChatEventPublisher;
import com.easychat.core.port.ChatMessageRepository;
import com.easychat.core.port.ModelCatalogRepository;
import com.easychat.core.port.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatUseCase {

    @Autowired
    private ReActAgent reactAgent;

    @Autowired
    private SessionUseCase sessionUseCase;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatEventPublisher chatEventPublisher;

    @Autowired
    private ModelCatalogRepository modelCatalogRepository;

    @Autowired
    private AgentCapabilityRegistry agentCapabilityRegistry;

    public ChatResult chat(ChatCommand command) {
        ChatSession session = resolveSession(command);
        ChatExecutionContext context = buildContext(command, session);
        long start = System.currentTimeMillis();
        AgentRequest agentRequest = buildAgentRequest(command, session);
        agentCapabilityRegistry.beforeRun(agentRequest, context);

        ChatMessage userMsg = insertMessage(session.getId(), "user", command.getUserMessage(), 1, context.getModelCode());
        publishMessageCreated(context, userMsg);

        String response = reactAgent.run(context.toAgentContext(command.getUserMessage())).getResponse();
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setContent(response);
        agentCapabilityRegistry.afterRun(agentResponse, context);

        ChatMessage aiMsg = insertMessage(session.getId(), "assistant", response, 1, context.getModelCode());
        publishMessageCreated(context, aiMsg);

        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.update(session);

        ChatCompletedEvent completedEvent = new ChatCompletedEvent();
        completedEvent.setSessionId(session.getId());
        completedEvent.setSessionCode(session.getSessionCode());
        completedEvent.setMessageId(aiMsg.getId());
        completedEvent.setModelCode(context.getModelCode());
        completedEvent.setLatencyMs((int) (System.currentTimeMillis() - start));
        chatEventPublisher.publish(completedEvent);

        ChatResult result = new ChatResult();
        result.setSessionCode(session.getSessionCode());
        result.setContent(response);
        return result;
    }

    private ChatSession resolveSession(ChatCommand command) {
        if (command.getSessionCode() != null) {
            return sessionUseCase.getSession(command.getSessionCode());
        }
        return sessionUseCase.createSession(command.getModelCode());
    }

    private ChatExecutionContext buildContext(ChatCommand command, ChatSession session) {
        ChatExecutionContext context = new ChatExecutionContext();
        context.setSessionId(session.getId());
        context.setSessionCode(session.getSessionCode());
        context.setModelCode(session.getModelCode());
        applyModelDefaults(context);
        context.setToolsEnabled(command.isToolsEnabled());
        context.setRagEnabled(command.isRagEnabled());
        return context;
    }

    private void applyModelDefaults(ChatExecutionContext context) {
        if (context.getModelCode() == null) {
            return;
        }
        ModelDefinition model = modelCatalogRepository.findModelByCode(context.getModelCode());
        if (model == null || model.getEnabled() != null && model.getEnabled() == 0) {
            return;
        }
        context.setMaxOutputTokens(model.getMaxOutputTokens());
        context.setDefaultTemperature(model.getDefaultTemperature());
        context.setDefaultTopP(model.getDefaultTopP());
        context.setDefaultConfig(model.getDefaultConfig());
    }

    private AgentRequest buildAgentRequest(ChatCommand command, ChatSession session) {
        AgentRequest request = new AgentRequest();
        request.setSessionId(session.getId());
        request.setUserMessage(command.getUserMessage());
        request.setToolsEnabled(command.isToolsEnabled());
        request.setRagEnabled(command.isRagEnabled());
        return request;
    }

    private ChatMessage insertMessage(Long sessionId, String role, String content, Integer status, String modelCode) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType("text");
        message.setMessageOrder(chatMessageRepository.nextMessageOrder(sessionId));
        message.setModelCode(modelCode);
        message.setStatus(status);
        message.setCreatedAt(LocalDateTime.now());
        return chatMessageRepository.insert(message);
    }

    private void publishMessageCreated(ChatExecutionContext context, ChatMessage message) {
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent();
        event.setSessionId(context.getSessionId());
        event.setSessionCode(context.getSessionCode());
        event.setMessageId(message.getId());
        event.setModelCode(context.getModelCode());
        event.setRole(message.getRole());
        chatEventPublisher.publish(event);
    }
}
