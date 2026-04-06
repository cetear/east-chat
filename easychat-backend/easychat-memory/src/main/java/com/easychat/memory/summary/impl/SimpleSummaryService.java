package com.easychat.memory.summary.impl;

import com.easychat.memory.summary.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SimpleSummaryService implements SummaryService {

    private static final int SUMMARY_THRESHOLD = 10;

    @Override
    public boolean shouldSummarize(Long sessionId, int messageCount) {
        return messageCount >= SUMMARY_THRESHOLD;
    }

    @Override
    public String generateSummary(Long sessionId) {
        // TODO: 使用 LLM 生成总结（在 llm 模块创建后集成）
        log.info("Generating summary for session: {}", sessionId);
        return "Summary placeholder";
    }
}
