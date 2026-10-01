package com.easychat.api.dto;

import lombok.Data;
import com.easychat.common.domain.chat.ChatSession;

@Data
public class SessionUpdateRequest {
    private String title, systemPrompt;
    private Integer maxRounds, status;

    public ChatSession toChanges() {
        var value = new ChatSession();
        value.setTitle(title);
        value.setSystemPrompt(systemPrompt);
        value.setMaxRounds(maxRounds);
        value.setStatus(status);
        return value;
    }
}
