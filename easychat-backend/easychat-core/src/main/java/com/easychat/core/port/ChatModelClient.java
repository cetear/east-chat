package com.easychat.core.port;

import com.easychat.core.context.ChatExecutionContext;
import reactor.core.publisher.Flux;

public interface ChatModelClient {
    String chat(String prompt, ChatExecutionContext context);

    Flux<String> streamChat(String prompt, ChatExecutionContext context);
}
