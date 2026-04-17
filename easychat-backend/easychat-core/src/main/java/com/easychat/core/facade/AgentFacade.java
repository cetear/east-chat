package com.easychat.core.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easychat.common.util.SSEUtil;
import com.easychat.core.agent.AgentEvent;
import com.easychat.core.agent.ReActAgent;
import com.easychat.core.context.AgentContext;
import com.easychat.core.router.ModelRouter;
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

    // ------------------------------------------------------------------ //
    //  公开方法
    // ------------------------------------------------------------------ //

    /**
     * 流式聊天。
     * 通过 {@link ModelRouter#MODEL_CODE_HOLDER} 将会话绑定的模型透传给路由器，
     * 使路由器能够按模型选择合适的渠道商。
     */
    public SseEmitter streamChat(Long sessionId, String userMessage, boolean toolsEnabled, boolean ragEnabled) {
        SseEmitter emitter = SSEUtil.createEmitter();

        try {
            // 保存用户消息
            ChatMessageDO userMsg = new ChatMessageDO("user", userMessage);
            userMsg.setSessionId(sessionId);
            userMsg.setMessageOrder(getNextMessageOrder(sessionId));
            userMsg.setStatus("DONE");
            conversationMemory.addMessage(userMsg);

            // 预建 AI 消息占位（PROCESSING 状态）
            ChatMessageDO aiMsg = new ChatMessageDO("assistant", "");
            aiMsg.setSessionId(sessionId);
            aiMsg.setMessageOrder(getNextMessageOrder(sessionId));
            aiMsg.setStatus("PROCESSING");
            conversationMemory.addMessage(aiMsg);

            // 构建 Agent 上下文
            AgentContext context = new AgentContext();
            context.setSessionId(sessionId);
            context.setUserMessage(userMessage);
            context.setToolsEnabled(toolsEnabled);
            context.setRagEnabled(ragEnabled);

            // 将会话关联的模型标识设置到 ThreadLocal，供 ModelRouter 使用
            String modelCode = resolveModelCode(sessionId);
            ModelRouter.MODEL_CODE_HOLDER.set(modelCode);

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
                    updateMessageFinal(aiMsg.getId(), "FAILED", fullResponse.toString(), modelCode);
                    SSEUtil.sendError(emitter, error.getMessage());
                    ModelRouter.MODEL_CODE_HOLDER.remove();
                },
                () -> {
                    updateMessageFinal(aiMsg.getId(), "DONE", fullResponse.toString(), modelCode);
                    if (chatEventProducer != null) {
                        chatEventProducer.sendChatEvent(sessionId.toString(), userMessage);
                    }
                    updateSessionTimestamp(sessionId);
                    SSEUtil.sendFinish(emitter, "stop");
                    ModelRouter.MODEL_CODE_HOLDER.remove();
                }
            );

        } catch (Exception e) {
            log.error("Stream chat failed", e);
            SSEUtil.sendError(emitter, e.getMessage());
            ModelRouter.MODEL_CODE_HOLDER.remove();
        }

        return emitter;
    }

    /**
     * 同步聊天。
     */
    public String chat(Long sessionId, String userMessage) {
        String modelCode = resolveModelCode(sessionId);
        ModelRouter.MODEL_CODE_HOLDER.set(modelCode);
        try {
            ChatMessageDO userMsg = new ChatMessageDO("user", userMessage);
            userMsg.setSessionId(sessionId);
            userMsg.setMessageOrder(getNextMessageOrder(sessionId));
            userMsg.setStatus("DONE");
            conversationMemory.addMessage(userMsg);

            AgentContext context = new AgentContext();
            context.setSessionId(sessionId);
            context.setUserMessage(userMessage);

            String response = reactAgent.run(context).getResponse();

            ChatMessageDO aiMsg = new ChatMessageDO("assistant", response);
            aiMsg.setSessionId(sessionId);
            aiMsg.setMessageOrder(getNextMessageOrder(sessionId));
            aiMsg.setStatus("DONE");
            aiMsg.setProviderCode(modelCode);
            conversationMemory.addMessage(aiMsg);

            if (chatEventProducer != null) {
                chatEventProducer.sendChatEvent(sessionId.toString(), userMessage);
            }
            updateSessionTimestamp(sessionId);
            return response;
        } catch (Exception e) {
            log.error("Chat failed", e);
            throw new RuntimeException("Chat failed", e);
        } finally {
            ModelRouter.MODEL_CODE_HOLDER.remove();
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
        List<ChatMessageDO> messages = chatMessageMapper.selectList(
            new QueryWrapper<ChatMessageDO>()
                .eq("session_id", session.getId())
                .orderByAsc("message_order"));
        session.setMessages(messages);
        return session;
    }

    public List<ChatSessionDO> getSessions() {
        return chatSessionMapper.selectList(null);
    }

    public void updateSession(ChatSessionDO session) {
        session.setUpdatedAt(System.currentTimeMillis());
        chatSessionMapper.updateById(session);
    }

    public void deleteSession(String sessionId) {
        ChatSessionDO session = getSession(sessionId);
        chatMessageMapper.delete(
            new QueryWrapper<ChatMessageDO>().eq("session_id", session.getId()));
        chatSessionMapper.deleteById(session.getId());
    }

    // ------------------------------------------------------------------ //
    //  私有方法
    // ------------------------------------------------------------------ //

    /**
     * 从会话中解析模型标识（modelType 字段）。
     * 未找到时退化为空字符串，让 ModelRouter 使用降级客户端。
     */
    private String resolveModelCode(Long sessionId) {
        try {
            ChatSessionDO session = chatSessionMapper.selectById(sessionId);
            return session != null && session.getModelType() != null ? session.getModelType() : "";
        } catch (Exception e) {
            log.warn("Failed to resolve model code for session {}", sessionId);
            return "";
        }
    }

    /**
     * 流式完成后更新 AI 消息状态和内容。
     */
    private void updateMessageFinal(Long msgId, String status, String content, String providerCode) {
        if (msgId == null) return;
        ChatMessageDO update = new ChatMessageDO();
        update.setId(msgId);
        update.setStatus(status);
        update.setContent(content);
        update.setProviderCode(providerCode);
        chatMessageMapper.updateById(update);
    }

    private int getNextMessageOrder(Long sessionId) {
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>().eq(ChatMessageDO::getSessionId, sessionId))
                .stream()
                .map(ChatMessageDO::getMessageOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private void updateSessionTimestamp(Long sessionId) {
        ChatSessionDO session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(System.currentTimeMillis());
            chatSessionMapper.updateById(session);
        }
    }
}
