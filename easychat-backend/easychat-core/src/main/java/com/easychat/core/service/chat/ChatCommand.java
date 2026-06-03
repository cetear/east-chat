package com.easychat.core.service.chat;

import lombok.Data;

@Data
public class ChatCommand {
    private String sessionCode;
    private String modelCode;
    private String userMessage;
    private boolean toolsEnabled;
    private boolean ragEnabled;
}
