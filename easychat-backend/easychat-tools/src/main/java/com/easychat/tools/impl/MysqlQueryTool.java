package com.easychat.tools.impl;

import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import com.easychat.tools.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MysqlQueryTool implements Tool {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public String name() {
        return "mysql_query";
    }

    @Override
    public String description() {
        return "Query MySQL database for chat messages";
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            Long sessionId = Long.valueOf(args.get("sessionId").toString());
            long count = chatMessageMapper.selectCount(null);
            return "Total messages: " + count;
        } catch (Exception e) {
            log.error("MySQL query failed", e);
            return "Error: " + e.getMessage();
        }
    }
}
