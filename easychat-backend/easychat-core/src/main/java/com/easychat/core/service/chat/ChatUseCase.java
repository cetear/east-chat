package com.easychat.core.service.chat;
import com.easychat.core.agent.AgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Service
public class ChatUseCase {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private ChatExecutionService execution;
    public ChatResult chat(ChatCommand command) {
        ChatResult result = new ChatResult();
        StringBuilder content = new StringBuilder();
        execution.execute(command).doOnNext(event -> {
            switch (event.getType()) {
                case MESSAGE -> content.append(event.getContent());
                case SOURCES -> result.setSources(event.getContent());
                case WARNING -> result.setWarning(event.getContent());
                case META -> {
                    var meta=event.getMetadata();result.setSessionCode((String)meta.get("sessionId"));
                    if(meta.containsKey("finishReason"))result.setFinishReason((String)meta.get("finishReason"));
                    if(meta.containsKey("usage"))result.setUsage((java.util.Map<String,Object>)meta.get("usage"));
                    if(meta.containsKey("providerCode"))result.setProviderCode((String)meta.get("providerCode"));
                }
                default -> {}
            }
        }).blockLast();
        result.setContent(content.toString()); if(result.getFinishReason()==null) result.setFinishReason("stop"); return result;
    }
}
