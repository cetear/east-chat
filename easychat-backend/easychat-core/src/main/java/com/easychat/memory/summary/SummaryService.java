package com.easychat.memory.summary;
import com.easychat.core.context.ChatExecutionContext;
public interface SummaryService {
    void summarize(ChatExecutionContext context);
    boolean shouldSummarize(Long sessionId, int messageCount);
}