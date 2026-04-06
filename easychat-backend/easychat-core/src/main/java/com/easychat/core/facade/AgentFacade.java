package com.easychat.core.facade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.util.SSEUtil;
import com.easychat.core.agent.AgentEvent;
import com.easychat.core.agent.ReActAgent;
import com.easychat.core.context.AgentContext;
import com.easychat.infra.kafka.ChatEventProducer;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.infra.mysql.entity.ChatSessionDO;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import com.easychat.infra.mysql.mapper.ChatSessionMapper;
import com.easychat.memory.ConversationMemory;
import com.easychat.memory.summary.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentFacade {

    @Autowired
    private ReActAgent reactAgent;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private SummaryService summaryService;

    @Autowired(required = false)
    private ChatEventProducer chatEventProducer;

    /**
     * 流式聊天
     */
    public SseEmitter streamChat(Long sessionId, String userMessage, boolean toolsEnabled, boolean ragEnabled) {
        SseEmitter emitter = SSEUtil.createEmitter();

        new Thread(() -> {
            try {
                // 保存用户消息
                ChatMessageDO userMsg = new ChatMessageDO("user", userMessage);
                userMsg.setSessionId(sessionId);
                userMsg.setMessageOrder(getNextMessageOrder(sessionId));
                conversationMemory.addMessage(userMsg);

                // 构建 Agent 上下文
                AgentContext context = new AgentContext();
                context.setSessionId(sessionId);
                context.setUserMessage(userMessage);
                context.setToolsEnabled(toolsEnabled);
                context.setRagEnabled(ragEnabled);

                // 流式执行 Agent
                StringBuilder fullResponse = new StringBuilder();
                Flux<AgentEvent> stream = reactAgent.streamRun(context);

                stream.subscribe(
                    event -> {
                        switch (event.getType()) {
                            case THOUGHT -> SSEUtil.sendThought(emitter, event.toJsonData());
                            case ACTION -> SSEUtil.sendAction(emitter, event.toJsonData());
                            case OBSERVATION -> SSEUtil.sendObservation(emitter, event.toJsonData());
                            case MESSAGE -> {
                                fullResponse.append(event.getContent());
                                SSEUtil.sendMessage(emitter, event.getContent());
                            }
                            case ERROR -> SSEUtil.sendError(emitter, event.getContent());
                            default -> { }
                        }
                    },
                    error -> {
                        log.error("Streaming error", error);
                        SSEUtil.sendError(emitter, error.getMessage());
                    },
                    () -> {
                        // 保存 AI 消息
                        ChatMessageDO aiMsg = new ChatMessageDO("assistant", fullResponse.toString());
                        aiMsg.setSessionId(sessionId);
                        aiMsg.setMessageOrder(getNextMessageOrder(sessionId));
                        conversationMemory.addMessage(aiMsg);

                        // 发送 Kafka 事件
                        if (chatEventProducer != null) {
                            chatEventProducer.sendChatEvent(sessionId.toString(), userMessage);
                        }

                        // 更新会话时间
                        updateSessionTimestamp(sessionId);

                        SSEUtil.sendFinish(emitter, "stop");
                    }
                );

            } catch (Exception e) {
                log.error("Stream chat failed", e);
                SSEUtil.sendError(emitter, e.getMessage());
            }
        }).start();

        return emitter;
    }

    /**
     * 同步聊天
     */
    public String chat(Long sessionId, String userMessage) {
        try {
            // 保存用户消息
            ChatMessageDO userMsg = new ChatMessageDO("user", userMessage);
            userMsg.setSessionId(sessionId);
            userMsg.setMessageOrder(getNextMessageOrder(sessionId));
            conversationMemory.addMessage(userMsg);

            // 构建 Agent 上下文
            AgentContext context = new AgentContext();
            context.setSessionId(sessionId);
            context.setUserMessage(userMessage);

            // 执行 Agent
            String response = reactAgent.run(context).getResponse();

            // 保存 AI 消息
            ChatMessageDO aiMsg = new ChatMessageDO("assistant", response);
            aiMsg.setSessionId(sessionId);
            aiMsg.setMessageOrder(getNextMessageOrder(sessionId));
            conversationMemory.addMessage(aiMsg);

            // 发送 Kafka 事件
            if (chatEventProducer != null) {
                chatEventProducer.sendChatEvent(sessionId.toString(), userMessage);
            }

            // 更新会话时间
            updateSessionTimestamp(sessionId);

            return response;
        } catch (Exception e) {
            log.error("Chat failed", e);
            throw new RuntimeException("Chat failed", e);
        }
    }

    /**
     * 创建会话
     */
    public ChatSessionDO createSession(String modelType) {
        long now = System.currentTimeMillis();
        ChatSessionDO session = new ChatSessionDO();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTitle("新会话");
        session.setModelType(modelType);
        session.setMaxRounds(10);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 获取会话
     */
    public ChatSessionDO getSession(String sessionId) {
        ChatSessionDO session = chatSessionMapper.selectOne(
            new QueryWrapper<ChatSessionDO>().eq("session_id", sessionId));
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }
        // 加载消息列表
        List<ChatMessageDO> messages = chatMessageMapper.selectList(
            new QueryWrapper<ChatMessageDO>()
                .eq("session_id", session.getId())
                .orderByAsc("message_order"));
        session.setMessages(messages);
        return session;
    }

    /**
     * 获取所有会话
     */
    public List<ChatSessionDO> getSessions() {
        return chatSessionMapper.selectList(null);
    }

    /**
     * 更新会话
     */
    public void updateSession(ChatSessionDO session) {
        session.setUpdatedAt(System.currentTimeMillis());
        chatSessionMapper.updateById(session);
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        ChatSessionDO session = getSession(sessionId);
        chatMessageMapper.delete(
            new QueryWrapper<ChatMessageDO>().eq("session_id", session.getId()));
        chatSessionMapper.deleteById(session.getId());
    }

    // ---- 私有方法 ----

    private int getNextMessageOrder(Long sessionId) {
        Integer maxOrder = chatMessageMapper.selectList(
            new QueryWrapper<ChatMessageDO>().eq("session_id", sessionId))
            .stream()
            .map(ChatMessageDO::getMessageOrder)
            .max(Integer::compareTo)
            .orElse(-1);
        return maxOrder + 1;
    }

    private void updateSessionTimestamp(Long sessionId) {
        ChatSessionDO session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(System.currentTimeMillis());
            chatSessionMapper.updateById(session);
        }
    }
}
