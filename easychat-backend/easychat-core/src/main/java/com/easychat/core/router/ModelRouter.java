package com.easychat.core.router;

import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.ChatModelClient;
import com.easychat.llm.client.LLMCallOptions;
import com.easychat.llm.client.LLMClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ModelRouter implements LLMClient, ChatModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final ThreadLocal<String> MODEL_CODE_HOLDER = new ThreadLocal<>();

    private final ProviderRegistry registry;
    private final LLMClient fallbackClient;

    public ModelRouter(ProviderRegistry registry, LLMClient fallbackClient) {
        this.registry = registry;
        this.fallbackClient = fallbackClient;
    }

    @Override
    public String chat(String prompt) {
        return chat(prompt, MODEL_CODE_HOLDER.get(), LLMCallOptions.empty());
    }

    public String chat(String prompt, String modelCode) {
        return chat(prompt, modelCode, LLMCallOptions.empty());
    }

    public String chat(String prompt, String modelCode, LLMCallOptions options) {
        List<ProviderWrapper> providers = modelCode != null ? registry.getProviders(modelCode) : List.of();

        if (providers.isEmpty()) {
            return fallbackClient.chat(prompt, options);
        }

        Exception lastError = null;
        for (ProviderWrapper wrapper : providers) {
            String providerCode = wrapper.getProvider().getProviderCode();
            if (!wrapper.isAvailable()) {
                log.debug("[{}] skipped (circuit open)", providerCode);
                continue;
            }
            try {
                String result = wrapper.getProvider().chat(prompt, options);
                wrapper.recordSuccess();
                return result;
            } catch (Exception e) {
                log.warn("[{}] chat failed: {}", providerCode, e.getMessage());
                wrapper.recordFailure();
                lastError = e;
            }
        }
        throw new RuntimeException("All providers failed for model: " + modelCode, lastError);
    }

    @Override
    public String chat(String prompt, ChatExecutionContext context) {
        return chat(prompt, context != null ? context.getModelCode() : null, toCallOptions(context));
    }

    @Override
    public Flux<String> streamChat(String prompt) {
        return streamChat(prompt, MODEL_CODE_HOLDER.get(), LLMCallOptions.empty());
    }

    public Flux<String> streamChat(String prompt, String modelCode) {
        return streamChat(prompt, modelCode, LLMCallOptions.empty());
    }

    public Flux<String> streamChat(String prompt, String modelCode, LLMCallOptions options) {
        List<ProviderWrapper> providers = modelCode != null ? registry.getProviders(modelCode) : List.of();

        if (providers.isEmpty()) {
            return fallbackClient.streamChat(prompt, options);
        }

        return Flux.create(sink -> attemptStream(
                providers,
                0,
                new StringBuilder(),
                prompt,
                options,
                new ArrayList<>(),
                modelCode,
                sink
        ));
    }

    @Override
    public Flux<String> streamChat(String prompt, ChatExecutionContext context) {
        return streamChat(prompt, context != null ? context.getModelCode() : null, toCallOptions(context));
    }

    private void attemptStream(List<ProviderWrapper> providers,
                               int index,
                               StringBuilder partial,
                               String originalPrompt,
                               LLMCallOptions options,
                               List<String> failures,
                               String modelCode,
                               reactor.core.publisher.FluxSink<String> sink) {
        if (sink.isCancelled()) {
            return;
        }
        if (index >= providers.size()) {
            String detail = failures.isEmpty() ? "no available provider" : String.join("; ", failures);
            sink.error(new RuntimeException("All providers exhausted during streaming for model: "
                    + modelCode + ". " + detail));
            return;
        }

        ProviderWrapper wrapper = providers.get(index);
        String providerCode = wrapper.getProvider().getProviderCode();

        if (!wrapper.isAvailable()) {
            log.debug("[{}] skipped (circuit open)", providerCode);
            failures.add(providerCode + " skipped: circuit open");
            attemptStream(providers, index + 1, partial, originalPrompt, options, failures, modelCode, sink);
            return;
        }

        String effectivePrompt = partial.isEmpty()
                ? originalPrompt
                : buildContinuationPrompt(originalPrompt, partial.toString());

        wrapper.getProvider().streamChat(effectivePrompt, options)
                .subscribe(
                        token -> {
                            if (!sink.isCancelled()) {
                                partial.append(token);
                                sink.next(token);
                            }
                        },
                        error -> {
                            if (sink.isCancelled()) {
                                return;
                            }
                            log.warn("[{}] stream interrupted after {} chars: {}",
                                    providerCode, partial.length(), error.getMessage());
                            wrapper.recordFailure();
                            failures.add(providerCode + " failed: " + error.getMessage());
                            attemptStream(providers, index + 1, partial, originalPrompt, options,
                                    failures, modelCode, sink);
                        },
                        () -> {
                            if (!sink.isCancelled()) {
                                wrapper.recordSuccess();
                                sink.complete();
                            }
                        }
                );
    }

    private String buildContinuationPrompt(String originalPrompt, String partial) {
        String tail = partial.length() > 200 ? partial.substring(partial.length() - 200) : partial;
        return originalPrompt + "\n\n"
                + "[Already generated content. Do not repeat it; continue from this point.]\n"
                + "..." + tail
                + "\n\nContinue the remaining answer with consistent meaning and style:\nAssistant: ";
    }

    private LLMCallOptions toCallOptions(ChatExecutionContext context) {
        if (context == null) {
            return LLMCallOptions.empty();
        }
        LLMCallOptions options = new LLMCallOptions();
        options.setModelName(context.getModelCode());
        options.setMaxTokens(context.getMaxOutputTokens());
        options.setTemperature(toDouble(context.getDefaultTemperature()));
        options.setTopP(toDouble(context.getDefaultTopP()));
        applyDefaultConfig(options, context.getDefaultConfig());
        return options;
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private void applyDefaultConfig(LLMCallOptions options, String defaultConfig) {
        if (defaultConfig == null || defaultConfig.isBlank()) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(defaultConfig);
            if (root.hasNonNull("stop")) {
                options.setStop(readStop(root.get("stop")));
            }
            if (root.hasNonNull("response_format")) {
                options.setResponseFormat(readResponseFormat(root.get("response_format")));
            }
            if (root.hasNonNull("seed")) {
                options.setSeed(root.get("seed").asInt());
            }
            if (root.hasNonNull("presence_penalty")) {
                options.setPresencePenalty(root.get("presence_penalty").asDouble());
            }
            if (root.hasNonNull("frequency_penalty")) {
                options.setFrequencyPenalty(root.get("frequency_penalty").asDouble());
            }
        } catch (Exception e) {
            log.warn("Invalid model default_config ignored: {}", e.getMessage());
        }
    }

    private List<String> readStop(JsonNode node) {
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (!node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        });
        return values.isEmpty() ? null : values;
    }

    private String readResponseFormat(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        JsonNode type = node.get("type");
        return type != null && type.isTextual() ? type.asText() : null;
    }
}
