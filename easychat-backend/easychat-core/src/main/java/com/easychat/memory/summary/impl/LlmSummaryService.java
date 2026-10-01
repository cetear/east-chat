package com.easychat.memory.summary.impl;
import com.easychat.common.port.ConversationMemory;
import com.easychat.memory.summary.SummaryService;
import com.easychat.llm.client.LLMClient;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.ChatModelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.stream.Collectors;
@Service
public class LlmSummaryService implements SummaryService {
    @Autowired private ConversationMemory conversationMemory;
    @Autowired private ChatModelClient chatModelClient;
    @Value("${easychat.memory.summary-threshold:10}") private int threshold = 10;
    @Value("${easychat.memory.summary-max-messages:40}") private int maxMessages = 40;
    @Value("${easychat.memory.summary-model:}") private String model;
    @Value("${easychat.memory.summary-keep-messages:4}") private int keep = 4;
    public boolean shouldSummarize(Long sessionId, int messageCount) { return messageCount >= Math.max(1,threshold) + Math.max(0,keep); }

    @Override public void summarize(ChatExecutionContext source) {
        Long id = source.getSessionId();
        int count = conversationMemory.countMessages(id);
        if (!shouldSummarize(id, count)) return;
        int limit = Math.min(Math.max(1, maxMessages), count - Math.max(0,keep));
        var batch = conversationMemory.unsummarized(id, limit);
        if (batch.isEmpty()) return;
        String previous = Objects.toString(conversationMemory.getSummary(id), "");
        StringBuilder transcript = new StringBuilder();
        long covered = 0;
        int budget = Math.max(source.getMaxContextChars(), 1024) - previous.length() - 200;
        for (var message : batch) {
            String line = message.getRole() + ": " + Objects.toString(message.getContent(), "") + "\n";
            if (transcript.length() + line.length() > budget) break;
            transcript.append(line); covered = message.getId();
        }
        if (covered == 0) return; // Do not mark unprocessed content as summarized.
        ChatExecutionContext call = new ChatExecutionContext();
        call.setModelCode(model == null || model.isBlank() ? source.getModelCode() : model);
        call.setDefaultModel(source.isDefaultModel() && Objects.equals(call.getModelCode(), source.getModelCode()));
        call.setMaxOutputTokens(1000);
        String result = chatModelClient.chat("请在2000字以内合并旧摘要和新增对话，保留事实、偏好与未完成任务。\n旧摘要:\n"
                + previous + "\n新增对话:\n" + transcript, call);
        long coveredMessage = covered;
        if (result != null && !result.isBlank()) source.writeGuarded(() -> conversationMemory.saveSummary(id,
                result.substring(0, Math.min(4000,result.length())), coveredMessage));
    }
}
