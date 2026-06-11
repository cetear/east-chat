package com.easychat.core.service.chat;

import com.easychat.core.domain.chat.ChatSession;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionView {
    private Long id;
    private String sessionCode;
    private String title;
    private String systemPrompt;
    private Integer maxRounds;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SessionView from(ChatSession session) {
        SessionView view = new SessionView();
        view.setId(session.getId());
        view.setSessionCode(session.getSessionCode());
        view.setTitle(session.getTitle());
        view.setSystemPrompt(session.getSystemPrompt());
        view.setMaxRounds(session.getMaxRounds());
        view.setStatus(session.getStatus());
        view.setCreatedAt(session.getCreatedAt());
        view.setUpdatedAt(session.getUpdatedAt());
        return view;
    }
}
