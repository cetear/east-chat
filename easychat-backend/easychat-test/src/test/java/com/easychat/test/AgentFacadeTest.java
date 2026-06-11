package com.easychat.test;

import com.easychat.app.EasyChatApplication;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.facade.AgentFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = EasyChatApplication.class)
public class AgentFacadeTest {

    @Autowired
    private AgentFacade agentFacade;

    @Test
    public void testCreateSession() {
        ChatSession session = agentFacade.createSession();
        assertNotNull(session);
        assertNotNull(session.getSessionCode());
    }

    @Test
    public void testGetSessions() {
        assertNotNull(agentFacade.getSessions());
    }
}
