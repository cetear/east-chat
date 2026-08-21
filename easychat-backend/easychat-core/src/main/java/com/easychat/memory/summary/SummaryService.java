package com.easychat.memory.summary;

public interface SummaryService {
    /**
     * 判断是否需要总结
     */
    boolean shouldSummarize(Long sessionId, int messageCount);

    /**
     * 生成会话总结
     */
    String generateSummary(Long sessionId);
}
