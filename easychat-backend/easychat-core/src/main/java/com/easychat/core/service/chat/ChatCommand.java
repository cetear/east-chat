package com.easychat.core.service.chat;

import lombok.Data;

import java.util.List;

@Data
public class ChatCommand {
    private com.easychat.common.security.Actor actor = com.easychat.common.security.Actor.demo();
    private String sessionCode;
    private String modelCode;
    private String userMessage;
    private boolean toolsEnabled;
    private boolean ragEnabled;
    private String dataset;
    private List<String> images;
}
