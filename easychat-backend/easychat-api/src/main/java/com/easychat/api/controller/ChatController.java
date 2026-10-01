package com.easychat.api.controller;

import com.easychat.api.dto.*;
import com.easychat.common.util.DatasetId;
import com.easychat.common.domain.chat.*;
import com.easychat.api.facade.AgentFacade;
import com.easychat.core.service.chat.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {
    @Autowired
    private AgentFacade agentFacade;

    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        return agentFacade.streamChat(command(request));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(ChatResponse.from(agentFacade.chat(command(request))));
    }

    private ChatCommand command(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty())
            throw bad("messages is required");
        MessageDTO message = request.getMessages().get(request.getMessages().size() - 1);
        if (message == null || !com.easychat.common.constant.MessageRole.USER.getValue().equals(message.getRole()))
            throw bad("last message role must be user");
        ChatCommand command = new ChatCommand();
        command.setActor(com.easychat.common.security.ActorContext.current());
        command.setUserMessage((message.getContent() == null || message.getContent().isBlank()) && message.getImages() != null && !message.getImages().isEmpty() ? "" : text(message.getContent(), "content"));
        command.setModelCode(text(request.getModel(), "model"));
        command.setSessionCode(request.getSessionId() == null || request.getSessionId().isBlank() ? null : request.getSessionId().trim());
        try {
            command.setDataset(DatasetId.normalize(request.getDataset()));
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
        command.setToolsEnabled(request.isToolsEnabled());
        command.setRagEnabled(request.isRagEnabled());
        command.setImages(message.getImages());
        return command;
    }

    private String text(String value, String name) {
        if (value == null || value.isBlank()) throw bad(name + " is required");
        return value.trim();
    }

    private ResponseStatusException bad(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }

    @PostMapping({"/session", "/createSession"})
    public ResponseEntity<SessionView> createSession() {
        return ResponseEntity.ok(SessionView.from(agentFacade.createSession()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionView>> getSessions() {
        return ResponseEntity.ok(agentFacade.getSessions().stream().map(SessionView::from).toList());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> getSession(@PathVariable("sessionId") String sessionId) {
        return ResponseEntity.ok(SessionView.from(agentFacade.getSession(sessionId)));
    }

    @GetMapping("/session/{sessionId}/messages")
    public List<ChatMessageView> history(@PathVariable("sessionId") String sessionId) {
        return agentFacade.history(sessionId).stream().map(ChatMessageView::from).toList();
    }

    @PutMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> updateSession(@PathVariable("sessionId") String sessionId, @RequestBody SessionUpdateRequest changes) {
        return ResponseEntity.ok(SessionView.from(agentFacade.updateSession(sessionId, changes.toChanges())));
    }

    @PutMapping("/session/{sessionId}/max-rounds")
    public ResponseEntity<SessionView> setMaxRounds(@PathVariable("sessionId") String sessionId, @RequestBody Map<String, Integer> request) {
        Integer rounds = request.get("maxRounds");
        if (rounds == null || rounds < 1 || rounds > 100) throw bad("maxRounds must be 1-100");
        SessionUpdateRequest changes = new SessionUpdateRequest();
        changes.setMaxRounds(rounds);
        return updateSession(sessionId, changes);
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        agentFacade.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
