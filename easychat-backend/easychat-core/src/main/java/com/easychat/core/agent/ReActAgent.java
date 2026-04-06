package com.easychat.core.agent;

import com.easychat.core.context.AgentContext;
import com.easychat.llm.client.LLMClient;
import com.easychat.memory.ConversationMemory;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.tools.Tool;
import com.easychat.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReActAgent implements StreamingAgent {

    @Autowired
    private LLMClient llmClient;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired(required = false)
    private ToolRegistry toolRegistry;

    @Override
    public AgentResult run(AgentContext context) {
        try {
            String prompt = buildPrompt(context);
            String response = llmClient.chat(prompt);
            return AgentResult.success(response);
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            return AgentResult.error(e.getMessage());
        }
    }

    @Override
    public Flux<AgentEvent> streamRun(AgentContext context) {
        // 判断是否启用工具模式
        boolean useTools = context.isToolsEnabled()
            && toolRegistry != null
            && !toolRegistry.getAllTools().isEmpty();

        if (!useTools) {
            // 简单流式模式（无工具，退化为纯 LLM 调用）
            return simpleStreamRun(context);
        }

        // ReAct 循环模式
        return reactStreamRun(context);
    }

    /**
     * 简单流式：直接将 LLM 输出逐 token 返回
     */
    private Flux<AgentEvent> simpleStreamRun(AgentContext context) {
        try {
            String prompt = buildPrompt(context);
            return llmClient.streamChat(prompt).map(AgentEvent::message);
        } catch (Exception e) {
            log.error("Simple streaming failed", e);
            return Flux.error(e);
        }
    }

    /**
     * ReAct 循环流式：Thought → Action → Observation → ... → Final Answer
     */
    private Flux<AgentEvent> reactStreamRun(AgentContext context) {
        return Flux.create(sink -> {
            try {
                String systemPrompt = buildReActPrompt(context);
                StringBuilder conversation = new StringBuilder(systemPrompt);

                for (int i = 0; i < context.getMaxIterations(); i++) {
                    // 同步调用 LLM 进行推理
                    String llmResponse = llmClient.chat(conversation.toString());
                    ReActStep step = ReActOutputParser.parse(llmResponse);

                    // 发射 Thought 事件
                    if (step.getThought() != null) {
                        sink.next(AgentEvent.thought(step.getThought()));
                    }

                    // 如果有最终答案
                    if (step.isFinished()) {
                        // 流式输出最终答案
                        String finalAnswer = step.getFinalAnswer();
                        // 逐块发送（模拟流式效果，每 20 个字符一块）
                        int chunkSize = 20;
                        for (int j = 0; j < finalAnswer.length(); j += chunkSize) {
                            String chunk = finalAnswer.substring(j, Math.min(j + chunkSize, finalAnswer.length()));
                            sink.next(AgentEvent.message(chunk));
                        }
                        sink.complete();
                        return;
                    }

                    // 如果有 Action，执行工具
                    if (step.hasAction()) {
                        Map<String, Object> input = parseActionInput(step.getActionInput());
                        sink.next(AgentEvent.action(step.getAction(), input));

                        // 执行工具
                        String toolResult;
                        Tool tool = toolRegistry.getTool(step.getAction());
                        if (tool != null) {
                            try {
                                toolResult = tool.execute(input);
                            } catch (Exception e) {
                                toolResult = "Error executing tool: " + e.getMessage();
                            }
                        } else {
                            toolResult = "Error: Tool '" + step.getAction() + "' not found. Available tools: "
                                + toolRegistry.getAllTools().values().stream().map(Tool::name).collect(Collectors.joining(", "));
                        }

                        sink.next(AgentEvent.observation(toolResult));

                        // 追加到对话历史
                        conversation.append("\n").append(llmResponse)
                            .append("\nObservation: ").append(toolResult).append("\n");
                    }
                }

                // 达到最大迭代次数
                sink.next(AgentEvent.thought("Maximum reasoning steps reached. Providing best available answer."));
                sink.next(AgentEvent.message("I was unable to fully resolve your request within the allowed reasoning steps. Please try simplifying your question."));
                sink.complete();

            } catch (Exception e) {
                log.error("ReAct streaming failed", e);
                sink.error(e);
            }
        });
    }

    /**
     * 构建简单对话 prompt
     */
    private String buildPrompt(AgentContext context) {
        StringBuilder prompt = new StringBuilder();

        List<ChatMessageDO> history = conversationMemory.getRecentMessages(
            context.getSessionId(), 10);

        if (!history.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (ChatMessageDO msg : history) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("User: ").append(context.getUserMessage()).append("\n");
        prompt.append("Assistant: ");

        return prompt.toString();
    }

    /**
     * 构建 ReAct 系统 prompt
     */
    private String buildReActPrompt(AgentContext context) {
        StringBuilder prompt = new StringBuilder();

        // 加载系统提示词模板
        String template = loadPromptTemplate();

        // 构建工具描述
        StringBuilder toolsDesc = new StringBuilder();
        for (Tool tool : toolRegistry.getAllTools().values()) {
            toolsDesc.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }

        // 替换模板变量
        String systemPrompt = template
            .replace("{{tools}}", toolsDesc.toString())
            .replace("{{maxIterations}}", String.valueOf(context.getMaxIterations()));

        prompt.append(systemPrompt).append("\n\n");

        // 加载历史消息
        List<ChatMessageDO> history = conversationMemory.getRecentMessages(
            context.getSessionId(), 10);

        if (!history.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (ChatMessageDO msg : history) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("User: ").append(context.getUserMessage()).append("\n");

        return prompt.toString();
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/react-system.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.warn("Failed to load ReAct prompt template, using default", e);
            return "You are a helpful AI assistant that can use tools.\n"
                + "Available tools:\n{{tools}}\n"
                + "Use Thought/Action/Action Input/Observation format.\n"
                + "When done, use Final Answer: <answer>";
        }
    }

    private Map<String, Object> parseActionInput(String actionInput) {
        Map<String, Object> result = new HashMap<>();
        if (actionInput == null || actionInput.isBlank()) {
            return result;
        }

        String trimmed = actionInput.trim();

        // 尝试简单 JSON 解析
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            // 简单的 key:value 解析
            String[] pairs = inner.split(",");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(':');
                if (colonIdx > 0) {
                    String key = pair.substring(0, colonIdx).trim().replace("\"", "");
                    String value = pair.substring(colonIdx + 1).trim().replace("\"", "");
                    result.put(key, value);
                }
            }
        } else {
            // 不是 JSON 格式，作为 query 参数
            result.put("query", trimmed);
        }

        return result;
    }
}
