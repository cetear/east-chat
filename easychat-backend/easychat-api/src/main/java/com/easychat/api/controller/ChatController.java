package com.easychat.api.controller;

import com.easychat.api.dto.ChatRequest;
import com.easychat.core.facade.AgentFacade;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.service.chat.SessionView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
        ChatSession session;
        if (request.getSessionId() != null) {
            session = agentFacade.getSession(request.getSessionId());
        } else {
            session = agentFacade.createSession(request.getModel());
        }

        String userMessage = request.getMessages().get(request.getMessages().size() - 1).getContent();
        return agentFacade.streamChat(session.getId(), userMessage, request.isToolsEnabled(), request.isRagEnabled());
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        try {
            ChatSession session;
            if (request.getSessionId() != null) {
                session = agentFacade.getSession(request.getSessionId());
            } else {
                session = agentFacade.createSession(request.getModel());
            }

            String userMessage = request.getMessages().get(request.getMessages().size() - 1).getContent();
            String response = agentFacade.chat(session.getId(), userMessage);

            return ResponseEntity.ok(Map.of(
                "content", response,
                "sessionId", session.getSessionCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/session")
    public ResponseEntity<SessionView> createSession(@RequestBody Map<String, String> request) {
        String modelType = request.get("modelType");
        ChatSession session = agentFacade.createSession(modelType);
        return ResponseEntity.ok(SessionView.from(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionView>> getSessions() {
        return ResponseEntity.ok(agentFacade.getSessions().stream().map(SessionView::from).toList());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(SessionView.from(agentFacade.getSession(sessionId)));
    }

    @PutMapping("/session/{sessionId}")
    public ResponseEntity<SessionView> updateSession(@PathVariable String sessionId, @RequestBody ChatSession session) {
        session.setSessionCode(sessionId);
        agentFacade.updateSession(session);
        return ResponseEntity.ok(SessionView.from(session));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        agentFacade.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/session/{sessionId}/max-rounds")
    public ResponseEntity<SessionView> setMaxRounds(@PathVariable String sessionId, @RequestBody Map<String, Integer> request) {
        ChatSession session = agentFacade.getSession(sessionId);
        session.setMaxRounds(request.get("maxRounds"));
        agentFacade.updateSession(session);
        return ResponseEntity.ok(SessionView.from(session));
    }
}
