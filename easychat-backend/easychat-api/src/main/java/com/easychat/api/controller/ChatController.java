package com.easychat.api.controller;

import com.easychat.api.dto.ChatRequest;
import com.easychat.api.dto.MessageDTO;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.facade.AgentFacade;
import com.easychat.core.service.chat.SessionView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private AgentFacade agentFacade;

    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        String modelCode = requireText(request.getModel(), "model is required");
        String userMessage = getLastUserMessage(request);

        ChatSession session = resolveOrCreateSession(request.getSessionId());
        return agentFacade.streamChat(session.getId(), modelCode, userMessage,
                request.isToolsEnabled(), request.isRagEnabled());
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        try {
            String modelCode = requireText(request.getModel(), "model is required");
            String userMessage = getLastUserMessage(request);

            ChatSession session = resolveOrCreateSession(request.getSessionId());
            String response = agentFacade.chat(session.getId(), modelCode, userMessage);

            return ResponseEntity.ok(Map.of(
                    "content", response,
                    "sessionId", session.getSessionCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping({"/session", "/createSession"})
    public ResponseEntity<SessionView> createSession() {
        ChatSession session = agentFacade.createSession();
        return ResponseEntity.ok(SessionView.from(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionView>> getSessions() {
        return ResponseEntity.ok(agentFacade.getSessions().stream().map(SessionView::from).toList());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> getSession(@PathVariable("sessionId") String sessionId) {
        return ResponseEntity.ok(SessionView.from(agentFacade.getSession(sessionId)));
    }

    @PutMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> updateSession(@PathVariable("sessionId") String sessionId,
                                                     @RequestBody ChatSession session) {
        ChatSession updated = agentFacade.updateSession(sessionId, session);
        return ResponseEntity.ok(SessionView.from(updated));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        agentFacade.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/session/{sessionId}/max-rounds")
    public ResponseEntity<SessionView> setMaxRounds(@PathVariable("sessionId") String sessionId,
                                                    @RequestBody Map<String, Integer> request) {
        ChatSession session = agentFacade.getSession(sessionId);
        session.setMaxRounds(request.get("maxRounds"));
        agentFacade.updateSession(session);
        return ResponseEntity.ok(SessionView.from(session));
    }

    private ChatSession resolveOrCreateSession(String sessionId) {
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return agentFacade.getSession(sessionId.trim());
        }
        return agentFacade.createSession();
    }

    private String getLastUserMessage(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw badRequest("messages is required");
        }
        MessageDTO lastMessage = request.getMessages().get(request.getMessages().size() - 1);
        if (lastMessage == null) {
            throw badRequest("last message is required");
        }
        return requireText(lastMessage.getContent(), "last message content is required");
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
