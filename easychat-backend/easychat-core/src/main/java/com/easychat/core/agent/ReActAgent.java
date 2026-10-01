package com.easychat.core.agent;

import com.easychat.core.context.AgentContext;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.ChatModelClient;
import com.easychat.llm.client.LLMClient;
import com.easychat.llm.client.LLMCallOptions;
import com.easychat.tools.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.CancellationException;

@Component
public class ReActAgent implements StreamingAgent {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    @Autowired
    private ChatModelClient chatModelClient;
    @Autowired
    private ToolRegistry toolRegistry;

    @Override
    public AgentResult run(AgentContext context) {
        try {
            String response = streamRun(context).filter(e -> e.getType() == AgentEvent.Type.MESSAGE)
                    .map(AgentEvent::getContent).collectList().map(parts -> String.join("", parts)).block();
            return AgentResult.success(response);
        } catch (Exception e) {
            return AgentResult.error(e.getMessage());
        }
    }

    @Override
    public Flux<AgentEvent> streamRun(AgentContext context) {
        return Flux.defer(() -> {
            ChatExecutionContext execution = execution(context);
            List<ChatMessage> messages = new ArrayList<>(execution.getModelMessages());
            boolean tools = context.isToolsEnabled();
            if (!tools) return chatModelClient.streamChat("", execution).map(AgentEvent::message);
            if (toolRegistry.getAllTools().isEmpty())
                return Flux.error(new IllegalStateException("No tools available"));
            messages.add(0, SystemMessage.from(toolPrompt(context.getMaxIterations())));
            return Flux.<AgentEvent>create(sink -> {
                try {
                    for (int round = 0; round < context.getMaxIterations(); round++) {
                        if (sink.isCancelled() || execution.isCancelled()) return;
                        int size = messages.stream().mapToInt(ReActAgent::messageSize).sum();
                        if (size > execution.getMaxContextChars())
                            throw new IllegalStateException("context_limit: tool reasoning input exceeds budget");
                        execution.checkLease();
                        execution.setModelMessages(List.copyOf(messages));
                        String answer = chatModelClient.chat("", execution);
                        if (sink.isCancelled() || execution.isCancelled()) return;
                        ReActStep step = ReActOutputParser.parse(answer);
                        if (step.isFinished()) {
                            sink.next(AgentEvent.message(step.getFinalAnswer()));
                            sink.complete();
                            return;
                        }
                        if (!step.hasAction())
                            throw new IllegalStateException("Invalid ReAct response: expected action or final answer");
                        Map<String, Object> input;
                        String observation;
                        try {
                            input = json.readValue(step.getActionInput() == null ? "{}" : step.getActionInput(),
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            if (input == null) throw new IllegalArgumentException("Tool input must be an object");
                        } catch (Exception e) {
                            messages.add(AiMessage.from(answer));
                            messages.add(UserMessage.from("Observation: invalid tool JSON input"));
                            sink.next(AgentEvent.observation("{\"success\":false,\"error\":\"invalid tool JSON input\"}"));
                            continue;
                        }
                        sink.next(AgentEvent.action(step.getAction(), input));
                        if (sink.isCancelled() || execution.isCancelled()) return;
                        try {
                            execution.checkLease();
                            Tool tool = toolRegistry.getTool(step.getAction());
                            if (tool == null) throw new IllegalArgumentException("Unknown tool: " + step.getAction());
                            String result = tool.execute(input, new ToolContext(context.getSessionId(), execution.getDataset(), execution.getUserId()));
                            observation = json.writeValueAsString(Map.of("success", true, "result", result == null ? "" : result));
                        } catch (Exception e) {
                            observation = json.writeValueAsString(Map.of("success", false, "error",
                                    Objects.toString(e.getMessage(), "Tool failed")));
                        }
                        if (sink.isCancelled() || execution.isCancelled()) return;
                        sink.next(AgentEvent.observation(observation));
                        messages.add(AiMessage.from(answer));
                        messages.add(UserMessage.from("Observation: " + observation));
                    }
                    sink.error(new IllegalStateException("max_iterations: tool reasoning limit reached"));
                } catch (Exception e) {
                    if (!sink.isCancelled()) sink.error(e);
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    private ChatExecutionContext execution(AgentContext context) {
        Object value = context.getVariable("chatExecutionContext");
        if (!(value instanceof ChatExecutionContext execution))
            throw new IllegalArgumentException("Chat execution context is required");
        if (execution.isCancelled()) throw new CancellationException();
        return execution;
    }

    private static int messageSize(ChatMessage message) {
        if (message instanceof UserMessage u)
            return u.contents().stream().mapToInt(c -> c instanceof TextContent t ? t.text().length() : 4096).sum();
        if (message instanceof AiMessage a) return a.text() == null ? 0 : a.text().length();
        if (message instanceof SystemMessage s) return s.text().length();
        return message.toString().length();
    }

    private String toolPrompt(int limit) {
        StringBuilder text = new StringBuilder("Use tools only when needed. Respond with Action: name\nAction Input: JSON object, "
                + "or Final Answer: answer. Wait for a real Observation; never invent tool results. Limit: " + limit + "\nTools:\n");
        toolRegistry.getAllTools().values().forEach(tool -> text.append(tool.name()).append(": ")
                .append(tool.description()).append(" Parameters: ").append(tool.parameters()).append("\n"));
        return text.toString();
    }
}
