package com.easychat.api.facade;

import com.easychat.api.sse.SSEUtil;
import com.easychat.core.agent.AgentEvent;
import com.easychat.common.domain.chat.*;
import com.easychat.core.service.chat.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposables;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentFacade {
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired
    private ChatUseCase chatUseCase;
    @Autowired
    private StreamChatUseCase streamChatUseCase;
    @Autowired
    private SessionUseCase sessionUseCase;

    public ChatResult chat(ChatCommand command) {
        return chatUseCase.chat(command);
    }

    public SseEmitter streamChat(ChatCommand command) {
        SseEmitter emitter = SSEUtil.createEmitter();
        var subscription = Disposables.swap();
        AtomicBoolean ended = new AtomicBoolean();
        var reason = new java.util.concurrent.atomic.AtomicReference<>("stop");
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            ended.set(true);
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(error -> {
            ended.set(true);
            subscription.dispose();
        });
        subscription.update(streamChatUseCase.streamChat(command).subscribe(event -> {
                    if(event.getType()==AgentEvent.Type.META && event.getMetadata().containsKey("finishReason"))reason.set((String)event.getMetadata().get("finishReason"));
            if (!ended.get() && !send(emitter, event)) {
                        ended.set(true);
                        subscription.dispose();
                        emitter.complete();
                    }
                }, error -> {
                    if (ended.compareAndSet(false, true)) SSEUtil.sendError(emitter, error.getMessage());
                },
                () -> {
                    if (ended.compareAndSet(false, true)) SSEUtil.sendFinish(emitter, reason.get());
                }));
        return emitter;
    }

    private String eventMetadata(AgentEvent event){try{return json.writeValueAsString(event.getMetadata());}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private boolean send(SseEmitter emitter, AgentEvent event) {
        return switch (event.getType()) {
            case THOUGHT -> SSEUtil.sendThought(emitter, eventData(event));
            case ACTION -> SSEUtil.sendAction(emitter, eventData(event));
            case OBSERVATION -> SSEUtil.sendObservation(emitter, eventData(event));
            case MESSAGE -> SSEUtil.sendMessage(emitter, event.getContent());
            case SOURCES -> SSEUtil.sendSources(emitter, event.getContent());
            case META -> SSEUtil.send(emitter,"meta",eventMetadata(event));
            case WARNING -> SSEUtil.send(emitter,"warning",event.getContent());
            default -> true;
        };
    }

    private String eventData(AgentEvent event) {
        try {
            return json.writeValueAsString(event.getType() == AgentEvent.Type.ACTION
                ? java.util.Map.of("tool", event.getToolName(), "input", event.getToolInput())
                : java.util.Map.of("content", event.getContent() == null ? "" : event.getContent()));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid event data", e); }
    }

    private ChatCommand legacy(Long id, String model, String text, boolean tools, boolean rag, List<String> images, String dataset) {
        ChatCommand command = new ChatCommand();
        command.setActor(com.easychat.common.security.ActorContext.current());
        command.setSessionCode(sessionUseCase.getSessionById(id).getSessionCode());
        command.setModelCode(model);
        command.setUserMessage(text);
        command.setToolsEnabled(tools);
        command.setRagEnabled(rag);
        command.setImages(images);
        command.setDataset(dataset);
        return command;
    }

    public ChatResult chat(Long id, String model, String text, boolean tools, boolean rag, List<String> images) {
        return chat(id, model, text, tools, rag, images, null);
    }

    public ChatResult chat(Long id, String model, String text, boolean tools, boolean rag, List<String> images, String dataset) {
        return chat(legacy(id, model, text, tools, rag, images, dataset));
    }

    public SseEmitter streamChat(Long id, String model, String text, boolean tools, boolean rag, List<String> images) {
        return streamChat(id, model, text, tools, rag, images, null);
    }

    public SseEmitter streamChat(Long id, String model, String text, boolean tools, boolean rag, List<String> images, String dataset) {
        return streamChat(legacy(id, model, text, tools, rag, images, dataset));
    }

    public ChatSession createSession() {
        return sessionUseCase.createSession();
    }

    public ChatSession getSession(String code) {
        return sessionUseCase.getSession(code);
    }

    public List<ChatSession> getSessions() {
        return sessionUseCase.getSessions();
    }

    public void updateSession(ChatSession session) {
        sessionUseCase.updateSession(session);
    }

    public ChatSession updateSession(String code, ChatSession changes) {
        return sessionUseCase.updateSession(code, changes);
    }

    public void deleteSession(String code) {
        sessionUseCase.deleteSession(code);
    }

    public List<ChatMessage> history(String code) {
        return sessionUseCase.history(code);
    }
}
