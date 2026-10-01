package com.easychat.core.service.chat;
import com.easychat.core.agent.*;
import com.easychat.core.capability.*;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.common.domain.chat.*;
import com.easychat.core.event.*;
import com.easychat.core.port.*;
import com.easychat.common.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.LocalDateTime;
import java.util.Map;

@lombok.extern.slf4j.Slf4j
@Service
public class ChatExecutionService {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private ReActAgent agent;
    @Autowired private ChatContextBuilder builder;
    @Autowired private ChatTurnPersistence persistence;
    @Autowired private ChatSessionRepository sessions;
    @Autowired private AgentCapabilityRegistry capabilities;
    @Autowired private ChatEventPublisher events;
    @Autowired(required=false) private com.easychat.core.event.DurableChatEvents outbox;
    @Autowired private SessionExecutionLock locks;

    public Flux<AgentEvent> execute(ChatCommand command) {
        return Flux.usingWhen(acquire(command),
            run -> Flux.defer(() -> {
                var output = agent.streamRun(run.context.toAgentContext(command.getUserMessage()))
                    .doOnNext(event -> { if (event.getType() == AgentEvent.Type.MESSAGE) run.append(event.getContent()); });
                Flux<AgentEvent> prefix = Flux.just(AgentEvent.meta(Map.of("sessionId", run.session.getSessionCode())));
                if (run.context.getRetrievedSourcesJson() != null) prefix = prefix.concatWithValues(AgentEvent.sources(run.context.getRetrievedSourcesJson()));
                if (run.context.getRetrievalWarning() != null) prefix = prefix.concatWithValues(AgentEvent.warning(run.context.getRetrievalWarning()));
                return prefix.concatWith(output).concatWith(Flux.defer(() -> Flux.just(AgentEvent.meta(Map.of(
                    "sessionId", run.session.getSessionCode(), "providerCode", java.util.Objects.toString(run.context.getProviderCode(), ""), "finishReason", java.util.Objects.toString(run.context.getModelFinishReason(),"stop"), "usage", run.context.usage())))));
            }),
            run -> cleanup(run, com.easychat.common.constant.MessageStatus.SUCCESS, null),
            (run, error) -> cleanup(run, com.easychat.common.constant.MessageStatus.FAILED, error.getMessage()),
            run -> { run.context.setCancelled(true); return cleanup(run, com.easychat.common.constant.MessageStatus.CANCELLED, "cancelled"); })
            .doOnDiscard(Run.class, run -> { run.context.setCancelled(true); cleanup(run, com.easychat.common.constant.MessageStatus.CANCELLED, "cancelled").subscribe(null, e -> log.error("Discarded chat cleanup failed", e)); })
            .subscribeOn(Schedulers.boundedElastic());
    }
    private Mono<Run> acquire(ChatCommand command) {
        return Mono.create(sink -> {
            var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            var ready = new java.util.concurrent.atomic.AtomicReference<Run>();
            sink.onCancel(() -> {
                cancelled.set(true);
                Run run = ready.get();
                if (run != null) cancelAcquisition(run);
            });
            // Let a short database transaction finish even if the HTTP subscriber disappears.
            Schedulers.boundedElastic().schedule(() -> {
                if (cancelled.get()) return;
                try {
                    Run run = start(command); ready.set(run);
                    if (cancelled.get()) cancelAcquisition(run); else sink.success(run);
                } catch (Exception e) { if (!cancelled.get()) sink.error(e); }
            });
        });
    }
    private void cancelAcquisition(Run run) {
        run.context.setCancelled(true);
        cleanup(run, com.easychat.common.constant.MessageStatus.CANCELLED, "cancelled").subscribe(null, e -> log.error("Cancelled chat initialization cleanup failed", e));
    }
    private Run start(ChatCommand command) throws Exception {
        ChatSession session = builder.resolveSession(command);
        var lock = locks.acquire(session.getId());
        try {
            ChatExecutionContext context = builder.buildContext(command, session); context.setLeaseCheck(lock::check);
            context.setDurableWrite(write -> lock.guarded(() -> { write.run(); return null; }));
            capabilities.beforeRun(builder.buildAgentRequest(command, session), context);
            builder.prepareMessages(command, context);
            Run run = new Run(session, context, lock);
            ChatMessage user = message(session.getId(), com.easychat.common.constant.MessageRole.USER.getValue(), command.getUserMessage(), context);
            if (command.getImages() != null && !command.getImages().isEmpty())
                user.setParamJson(json(Map.of("images", command.getImages())));
            ChatMessage[] turn = lock.guarded(() -> persistence.begin(user, message(session.getId(), com.easychat.common.constant.MessageRole.ASSISTANT.getValue(), "", context)));
            run.user = turn[0]; run.assistant = turn[1];
            return run;
        } catch (Exception e) { lock.close(); throw e; }
    }
    private Mono<Void> cleanup(Run run, int status, String error) {
        return Mono.<Void>fromRunnable(() -> {
            if (!run.cleaned.compareAndSet(false, true)) return;
            try {
                run.user.setStatus(status);
                run.assistant.setStatus(status); run.assistant.setContent(run.text());
                run.assistant.setErrorMsg(error == null ? null : error.substring(0, Math.min(500, error.length())));
                run.assistant.setFinishReason(status == com.easychat.common.constant.MessageStatus.SUCCESS ? java.util.Objects.toString(run.context.getModelFinishReason(),"stop") : status == com.easychat.common.constant.MessageStatus.CANCELLED ? "cancelled" : error != null && error.startsWith("max_iterations:") ? "max_iterations" : error != null && error.startsWith("context_limit:") ? "context_limit" : "error");
                run.assistant.setProviderCode(run.context.getProviderCode());
                run.assistant.setLatencyMs((int)Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - run.started));
                var usage=run.context.usage();
                if(Boolean.TRUE.equals(usage.get("complete"))) {
                    run.assistant.setPromptTokens(Math.toIntExact((Long)usage.get("promptTokens")));
                    run.assistant.setCompletionTokens(Math.toIntExact((Long)usage.get("completionTokens")));
                    run.assistant.setTotalTokens(Math.toIntExact((Long)usage.get("totalTokens")));
                }
                run.lock.guarded(() -> {persistence.finish(run.user, run.assistant);return null;});
                if (status == com.easychat.common.constant.MessageStatus.SUCCESS) {
                                        ChatSession touched = new ChatSession(); touched.setId(run.session.getId()); touched.setUpdatedAt(LocalDateTime.now());
                    try { sessions.update(touched); } catch (Exception e) { log.warn("Session timestamp update failed", e); }
                    AgentResponse response = new AgentResponse(); response.setContent(run.text());
                    try { capabilities.afterRun(response, run.context); } catch (Exception e) { log.warn("Post-chat capability failed", e); }
                    publishCreated(run, run.user); publishCreated(run, run.assistant);
                    ChatCompletedEvent event = new ChatCompletedEvent();
                    event.setSessionId(run.session.getId()); event.setSessionCode(run.session.getSessionCode());
                    event.setMessageId(run.assistant.getId()); event.setModelCode(run.context.getModelCode()); event.setProviderCode(run.context.getProviderCode());
                    event.setLatencyMs(run.assistant.getLatencyMs()); publish(event);
                }
            } finally { try { run.lock.close(); } catch (Exception e) { throw new IllegalStateException(e); } }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    private ChatMessage message(Long session, String role, String text, ChatExecutionContext context) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session); message.setRole(role); message.setContent(text); message.setContentType("text");
        message.setStatus(com.easychat.common.constant.MessageStatus.RUNNING); message.setModelCode(context.getModelCode()); message.setCreatedAt(LocalDateTime.now());
        return message;
    }
    private void publishCreated(Run run, ChatMessage message) {
        ChatMessageCreatedEvent event = new ChatMessageCreatedEvent();
        event.setSessionId(run.session.getId()); event.setSessionCode(run.session.getSessionCode());
        event.setMessageId(message.getId()); event.setModelCode(run.context.getModelCode()); event.setRole(message.getRole()); publish(event);
    }
    private void publish(com.easychat.core.event.ChatEvent event) {
        if(outbox!=null) return;
        try { events.publish(event); } catch (Exception e) { log.warn("Post-chat event failed", e); }
    }
    private String json(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    private static final class Run {
        final ChatSession session; final ChatExecutionContext context; final SessionExecutionLock.Guard lock;
        final long started = System.currentTimeMillis(); final StringBuilder content = new StringBuilder();
        final java.util.concurrent.atomic.AtomicBoolean cleaned = new java.util.concurrent.atomic.AtomicBoolean();
        ChatMessage user; ChatMessage assistant;
        Run(ChatSession s, ChatExecutionContext c, SessionExecutionLock.Guard l) { session=s; context=c; lock=l; }
        synchronized void append(String text) { content.append(text); }
        synchronized String text() { return content.toString(); }
    }
}
