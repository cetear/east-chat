package com.easychat.core.service.chat;
import com.easychat.core.agent.AgentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
@Service
public class StreamChatUseCase {
    @Autowired private ChatExecutionService execution;
    public Flux<AgentEvent> streamChat(ChatCommand command) { return execution.execute(command); }
}
