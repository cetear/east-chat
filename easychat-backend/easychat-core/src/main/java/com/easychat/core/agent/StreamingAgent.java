package com.easychat.core.agent;

import com.easychat.core.context.AgentContext;
import reactor.core.publisher.Flux;

public interface StreamingAgent extends Agent {
    /**
     * 流式执行，返回结构化事件流
     */
    Flux<AgentEvent> streamRun(AgentContext context);
}
