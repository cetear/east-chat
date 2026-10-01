package com.easychat.tools.impl;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ChatMessageRepository;
import com.easychat.tools.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class MysqlQueryTool implements Tool {
    @Autowired private ChatMessageRepository messages;
    public String name() { return "mysql_query"; }
    public String description() { return "Count successful messages in the current session. No arguments."; }
    public String execute(Map<String,Object> args) { throw new IllegalArgumentException("Trusted session context required"); }
    public String execute(Map<String,Object> args,ToolContext context) {
        if (context==null || context.sessionId()==null) throw new IllegalArgumentException("Session required");
        return "Total messages: " + messages.countSuccessful(context.sessionId());
    }
}
