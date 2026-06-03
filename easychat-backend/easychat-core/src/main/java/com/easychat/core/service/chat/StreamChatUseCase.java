package com.easychat.core.service.chat;

import com.easychat.core.agent.AgentEvent;
import com.easychat.core.agent.ReActAgent;
import com.easychat.core.capability.AgentCapabilityRegistry;
import com.easychat.core.capability.AgentRequest;
import com.easychat.core.capability.AgentResponse;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.domain.chat.ChatMessage;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.event.ChatCompletedEvent;
import com.easychat.core.port.ChatEventPublisher;
import com.easychat.core.port.ChatMessageRepository;
import com.easychat.core.port.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Service
public class StreamChatUseCase {

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
    private AgentCapabilityRegistry agentCapabilityRegistry;

    public Flux<AgentEvent> streamChat(ChatCommand command) {
        ChatSession session = resolveSession(command);
        ChatExecutionContext context = buildContext(command, session);
        AgentRequest agentRequest = buildAgentRequest(command, session);
        agentCapabilityRegistry.beforeRun(agentRequest, context);

        insertMessage(session.getId(), "user", command.getUserMessage(), 1, context.getModelCode());
        ChatMessage aiMsg = insertMessage(session.getId(), "assistant", "", 0, context.getModelCode());

        StringBuilder fullResponse = new StringBuilder();
        long start = System.currentTimeMillis();

        return reactAgent.streamRun(context.toAgentContext(command.getUserMessage()))
                .doOnNext(event -> {
                    if (event.getType() == AgentEvent.Type.MESSAGE) {
                        fullResponse.append(event.getContent());
                    }
                })
                .doOnError(error -> updateMessage(aiMsg.getId(), 0, fullResponse.toString(), context.getModelCode()))
                .doOnComplete(() -> {
                    updateMessage(aiMsg.getId(), 1, fullResponse.toString(), context.getModelCode());
                    AgentResponse agentResponse = new AgentResponse();
                    agentResponse.setContent(fullResponse.toString());
                    agentCapabilityRegistry.afterRun(agentResponse, context);
                    session.setUpdatedAt(LocalDateTime.now());
                    chatSessionRepository.update(session);

                    ChatCompletedEvent event = new ChatCompletedEvent();
                    event.setSessionId(context.getSessionId());
                    event.setSessionCode(context.getSessionCode());
                    event.setMessageId(aiMsg.getId());
                    event.setModelCode(context.getModelCode());
                    event.setLatencyMs((int) (System.currentTimeMillis() - start));
                    chatEventPublisher.publish(event);
                });
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
        context.setToolsEnabled(command.isToolsEnabled());
        context.setRagEnabled(command.isRagEnabled());
        return context;
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

    private void updateMessage(Long messageId, Integer status, String content, String modelCode) {
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setStatus(status);
        message.setContent(content);
        message.setModelCode(modelCode);
        chatMessageRepository.update(message);
    }
}
