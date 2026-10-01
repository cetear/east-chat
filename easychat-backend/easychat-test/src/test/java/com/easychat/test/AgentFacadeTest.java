package com.easychat.test;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.easychat.app.EasyChatApplication;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.api.facade.AgentFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = EasyChatApplication.class)
public class AgentFacadeTest {

    @Autowired
    private AgentFacade agentFacade;

    @Autowired
    private ElasticsearchClient elasticsearchClient;  // Spring Boot 自动注入

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

    @Test void testES() {
        try {
            var info = elasticsearchClient.info();
            System.out.println("链接成功---" + info.clusterName() + "-----");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
