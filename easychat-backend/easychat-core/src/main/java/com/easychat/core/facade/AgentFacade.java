package com.easychat.core.facade;

import com.easychat.common.util.SSEUtil;
import com.easychat.core.agent.AgentEvent;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.service.chat.ChatCommand;
import com.easychat.core.service.chat.ChatResult;
import com.easychat.core.service.chat.ChatUseCase;
import com.easychat.core.service.chat.SessionUseCase;
import com.easychat.core.service.chat.StreamChatUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentFacade {

    @Autowired
    private ChatUseCase chatUseCase;

    @Autowired
    private StreamChatUseCase streamChatUseCase;

    @Autowired
    private SessionUseCase sessionUseCase;

    public SseEmitter streamChat(Long sessionId, String modelCode, String userMessage, boolean toolsEnabled, boolean ragEnabled, List<String> images) {
        ChatSession session = sessionUseCase.getSessionById(sessionId);

        ChatCommand command = new ChatCommand();
        command.setSessionCode(session.getSessionCode());
        command.setModelCode(modelCode);
        command.setUserMessage(userMessage);
        command.setToolsEnabled(toolsEnabled);
        command.setRagEnabled(ragEnabled);
        command.setImages(images);

        SseEmitter emitter = SSEUtil.createEmitter();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        emitter.onCompletion(() -> dispose(subscriptionRef));
        emitter.onTimeout(() -> dispose(subscriptionRef));
        emitter.onError(error -> dispose(subscriptionRef));

        Disposable subscription = streamChatUseCase.streamChat(command).subscribe(
                event -> {
                    if (!sendEvent(emitter, event)) {
                        dispose(subscriptionRef);
                    }
                },
                error -> SSEUtil.sendError(emitter, error.getMessage()),
                () -> SSEUtil.sendFinish(emitter, "stop")
        );
        subscriptionRef.set(subscription);
        return emitter;
    }

    public String chat(Long sessionId, String modelCode, String userMessage, boolean toolsEnabled, boolean ragEnabled, List<String> images) {
        ChatSession session = sessionUseCase.getSessionById(sessionId);

        ChatCommand command = new ChatCommand();
        command.setSessionCode(session.getSessionCode());
        command.setModelCode(modelCode);
        command.setUserMessage(userMessage);
        command.setToolsEnabled(toolsEnabled);
        command.setRagEnabled(ragEnabled);
        command.setImages(images);
        ChatResult result = chatUseCase.chat(command);
        return result.getContent();
    }

    public ChatSession createSession() {
        return sessionUseCase.createSession();
    }

    public ChatSession getSession(String sessionCode) {
        return sessionUseCase.getSession(sessionCode);
    }

    public List<ChatSession> getSessions() {
        return sessionUseCase.getSessions();
    }

    public void updateSession(ChatSession session) {
        sessionUseCase.updateSession(session);
    }

    public ChatSession updateSession(String sessionCode, ChatSession changes) {
        return sessionUseCase.updateSession(sessionCode, changes);
    }

    public void deleteSession(String sessionCode) {
        sessionUseCase.deleteSession(sessionCode);
    }

    private boolean sendEvent(SseEmitter emitter, AgentEvent event) {
        return switch (event.getType()) {
            case THOUGHT -> SSEUtil.sendThought(emitter, event.toJsonData());
            case ACTION -> SSEUtil.sendAction(emitter, event.toJsonData());
            case OBSERVATION -> SSEUtil.sendObservation(emitter, event.toJsonData());
            case MESSAGE -> SSEUtil.sendMessage(emitter, event.getContent());
            case ERROR -> SSEUtil.sendError(emitter, event.getContent());
            default -> true;
        };
    }

    private void dispose(AtomicReference<Disposable> subscriptionRef) {
        Disposable subscription = subscriptionRef.get();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
