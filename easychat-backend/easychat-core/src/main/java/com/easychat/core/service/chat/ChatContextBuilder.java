package com.easychat.core.service.chat;

import com.easychat.common.exception.BusinessException;
import com.easychat.core.capability.AgentRequest;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.domain.chat.ChatSession;
import com.easychat.core.domain.model.ModelDefinition;
import com.easychat.core.port.ModelCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatContextBuilder {

    @Autowired
    private SessionUseCase sessionUseCase;

    @Autowired
    private ModelCatalogRepository modelCatalogRepository;

    public ChatSession resolveSession(ChatCommand command) {
        if (command.getSessionCode() != null) {
            return sessionUseCase.getSession(command.getSessionCode());
        }
        return sessionUseCase.createSession();
    }

    public ChatExecutionContext buildContext(ChatCommand command, ChatSession session) {
        ChatExecutionContext context = new ChatExecutionContext();
        context.setSessionId(session.getId());
        context.setSessionCode(session.getSessionCode());
        context.setModelCode(command.getModelCode());
        applyModelDefaults(context);
        context.setToolsEnabled(command.isToolsEnabled());
        context.setRagEnabled(command.isRagEnabled());
        context.setImages(command.getImages());
        validateVisionSupport(context);
        return context;
    }

    public AgentRequest buildAgentRequest(ChatCommand command, ChatSession session) {
        AgentRequest request = new AgentRequest();
        request.setSessionId(session.getId());
        request.setUserMessage(command.getUserMessage());
        request.setToolsEnabled(command.isToolsEnabled());
        request.setRagEnabled(command.isRagEnabled());
        return request;
    }

    private void applyModelDefaults(ChatExecutionContext context) {
        if (context.getModelCode() == null) {
            return;
        }
        ModelDefinition model = modelCatalogRepository.findModelByCode(context.getModelCode());
        if (model == null || model.getEnabled() != null && model.getEnabled() == 0) {
            return;
        }
        context.setMaxOutputTokens(model.getMaxOutputTokens());
        context.setDefaultTemperature(model.getDefaultTemperature());
        context.setDefaultTopP(model.getDefaultTopP());
        context.setDefaultConfig(model.getDefaultConfig());
        context.setSupportVision(model.getSupportVision() != null && model.getSupportVision() == 1);
    }

    private void validateVisionSupport(ChatExecutionContext context) {
        if (context.getImages() == null || context.getImages().isEmpty()) {
            return;
        }
        if (!context.isSupportVision()) {
            throw new BusinessException("当前模型不支持图片输入");
        }
    }
}
