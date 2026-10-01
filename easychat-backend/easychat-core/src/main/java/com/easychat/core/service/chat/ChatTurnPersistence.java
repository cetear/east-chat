package com.easychat.core.service.chat;

import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions only: never hold a database transaction during model/tool calls. */
@Service
public class ChatTurnPersistence {
    @Autowired private ChatMessageRepository messages;
    @Autowired(required=false) private com.easychat.core.event.DurableChatEvents outbox;

    @Transactional
    public ChatMessage[] begin(ChatMessage user, ChatMessage assistant) {
        return new ChatMessage[]{messages.insert(user), messages.insert(assistant)};
    }

    @Transactional
    public void finish(ChatMessage user, ChatMessage assistant) {
        messages.update(user);
        messages.update(assistant);
        if(outbox!=null) outbox.appendTurn(user,assistant);
    }
}
