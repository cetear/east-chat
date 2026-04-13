package com.easychat.test;

import com.easychat.app.EasyChatApplication;
import com.easychat.core.facade.AgentFacade;
import com.easychat.infra.mysql.entity.ChatSessionDO;
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
        ChatSessionDO session = agentFacade.createSession("gpt-4");
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals("gpt-4", session.getModelType());
    }

    @Test
    public void testGetSessions() {
        assertNotNull(agentFacade.getSessions());
    }
}
