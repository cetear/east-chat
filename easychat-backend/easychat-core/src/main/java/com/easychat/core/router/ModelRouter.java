package com.easychat.core.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.ChatModelClient;
import com.easychat.llm.client.LLMCallOptions;
import com.easychat.llm.client.LLMClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 多渠道路由器，实现 {@link LLMClient} 接口，无缝替换原单渠道客户端。
 *
 * <p>路由策略：
 * <ol>
 *   <li>按优先级遍历可用渠道（未熔断）</li>
 *   <li>流式场景：若渠道中途中断，将已生成内容拼入续写 prompt，切换下一渠道继续输出</li>
 *   <li>所有渠道均失败时抛出异常</li>
 * </ol>
 *
 * <p>调用方通过 {@link #MODEL_CODE_HOLDER} 注入当前请求的模型标识。
 * 若未设置，则退化为只调用 {@code fallbackClient}（原 LangChain4j 默认实现）。
 */
@Slf4j
public class ModelRouter implements LLMClient, ChatModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 调用方在使用前通过此 ThreadLocal 指定模型，调用结束后需 clear */
    public static final ThreadLocal<String> MODEL_CODE_HOLDER = new ThreadLocal<>();


    private final ProviderRegistry registry;

    /**
     * 降级客户端：当 DB 中无对应模型配置时使用原始 LangChain4j 客户端。
     * 使用 @Qualifier 或条件注入，此处允许为 null。
     */
    private final LLMClient fallbackClient;

    public ModelRouter(ProviderRegistry registry, LLMClient fallbackClient) {
        this.registry = registry;
        this.fallbackClient = fallbackClient;
    }

    // ------------------------------------------------------------------ //
    //  LLMClient 接口实现
    // ------------------------------------------------------------------ //

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
            if (!wrapper.isAvailable()) {
                log.debug("[{}] skipped (circuit open)", wrapper.getProvider().getProviderCode());
                continue;
            }
            try {
                String result = wrapper.getProvider().chat(prompt, options);
                wrapper.recordSuccess();
                return result;
            } catch (Exception e) {
                log.warn("[{}] chat failed: {}", wrapper.getProvider().getProviderCode(), e.getMessage());
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

        return Flux.create(sink -> attemptStream(providers, 0, new StringBuilder(), prompt, options, sink));
    }

    @Override
    public Flux<String> streamChat(String prompt, ChatExecutionContext context) {
        return streamChat(prompt, context != null ? context.getModelCode() : null, toCallOptions(context));
    }

    // ------------------------------------------------------------------ //
    //  流式分发与续写
    // ------------------------------------------------------------------ //

    private void attemptStream(List<ProviderWrapper> providers,
                               int index,
                               StringBuilder partial,
                               String originalPrompt,
                               LLMCallOptions options,
                               reactor.core.publisher.FluxSink<String> sink) {
        if (index >= providers.size()) {
            sink.error(new RuntimeException("All providers exhausted during streaming"));
            return;
        }

        ProviderWrapper wrapper = providers.get(index);

        if (!wrapper.isAvailable()) {
            log.debug("[{}] skipped (circuit open)", wrapper.getProvider().getProviderCode());
            attemptStream(providers, index + 1, partial, originalPrompt, options, sink);
            return;
        }

        String effectivePrompt = partial.isEmpty()
                ? originalPrompt
                : buildContinuationPrompt(originalPrompt, partial.toString());

        wrapper.getProvider().streamChat(effectivePrompt, options)
                .subscribe(
                        token -> {
                            partial.append(token);
                            sink.next(token);
                        },
                        error -> {
                            log.warn("[{}] stream interrupted after {} chars: {}",
                                    wrapper.getProvider().getProviderCode(), partial.length(), error.getMessage());
                            wrapper.recordFailure();
                            // 无缝切换到下一渠道
                            attemptStream(providers, index + 1, partial, originalPrompt, options, sink);
                        },
                        () -> {
                            wrapper.recordSuccess();
                            sink.complete();
                        }
                );
    }

    /**
     * 构建续写 Prompt：告知模型已生成的内容，要求从中断处继续。
     * 取 partial 末尾 200 字符作为上下文窗口，避免 tokenizer 差异导致的溢出。
     */
    private String buildContinuationPrompt(String originalPrompt, String partial) {
        String tail = partial.length() > 200 ? partial.substring(partial.length() - 200) : partial;
        return originalPrompt + "\n\n"
                + "【已生成内容（请勿重复，直接从此处继续）】\n"
                + "..." + tail
                + "\n\n请保持语义和风格一致，继续生成剩余内容：\nAssistant: ";
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
