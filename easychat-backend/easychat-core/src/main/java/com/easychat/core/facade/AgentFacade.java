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

import java.util.List;

@Service
public class AgentFacade {

    @Autowired
    private ChatUseCase chatUseCase;

    @Autowired
    private StreamChatUseCase streamChatUseCase;

    @Autowired
    private SessionUseCase sessionUseCase;

    public SseEmitter streamChat(Long sessionId, String userMessage, boolean toolsEnabled, boolean ragEnabled) {
        ChatSession session = sessionUseCase.getSessionById(sessionId);

        ChatCommand command = new ChatCommand();
        command.setSessionCode(session.getSessionCode());
        command.setUserMessage(userMessage);
        command.setToolsEnabled(toolsEnabled);
        command.setRagEnabled(ragEnabled);

        SseEmitter emitter = SSEUtil.createEmitter();
        streamChatUseCase.streamChat(command).subscribe(
                event -> sendEvent(emitter, event),
                error -> SSEUtil.sendError(emitter, error.getMessage()),
                () -> SSEUtil.sendFinish(emitter, "stop")
        );
        return emitter;
    }

    public String chat(Long sessionId, String userMessage) {
        ChatSession session = sessionUseCase.getSessionById(sessionId);

        ChatCommand command = new ChatCommand();
        command.setSessionCode(session.getSessionCode());
        command.setUserMessage(userMessage);
        ChatResult result = chatUseCase.chat(command);
        return result.getContent();
    }

    public ChatSession createSession(String modelCode) {
        return sessionUseCase.createSession(modelCode);
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

    public void deleteSession(String sessionCode) {
        sessionUseCase.deleteSession(sessionCode);
    }

    private void sendEvent(SseEmitter emitter, AgentEvent event) {
        switch (event.getType()) {
            case THOUGHT -> SSEUtil.sendThought(emitter, event.toJsonData());
            case ACTION -> SSEUtil.sendAction(emitter, event.toJsonData());
            case OBSERVATION -> SSEUtil.sendObservation(emitter, event.toJsonData());
            case MESSAGE -> SSEUtil.sendMessage(emitter, event.getContent());
            case ERROR -> SSEUtil.sendError(emitter, event.getContent());
            default -> { }
        }
    }
}
