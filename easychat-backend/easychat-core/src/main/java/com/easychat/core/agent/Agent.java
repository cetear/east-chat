package com.easychat.core.agent;

import com.easychat.core.context.AgentContext;
import reactor.core.publisher.Flux;

public interface Agent {
    /**
     * 同步执行
     */
    AgentResult run(AgentContext context);
}
