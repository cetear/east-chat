package com.easychat.core.router;
import com.easychat.core.context.ChatExecutionContext;
import com.easychat.core.port.ChatModelClient;
import com.easychat.llm.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class ModelRouter implements LLMClient, ChatModelClient {
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @org.springframework.beans.factory.annotation.Value("${easychat.router.strategy:priority}") private String strategy = "priority";
    private java.util.function.LongUnaryOperator draw = bound -> java.util.concurrent.ThreadLocalRandom.current().nextLong(bound);
    private final ProviderRegistry registry;
    private final LLMClient fallbackClient;
    public ModelRouter(ProviderRegistry registry, LLMClient fallback) { this.registry=registry; this.fallbackClient=fallback; }
    public String chat(String prompt) { ChatExecutionContext c=new ChatExecutionContext(); c.setDefaultModel(true); return chat(prompt,c); }
    public Flux<String> streamChat(String prompt) { ChatExecutionContext c=new ChatExecutionContext(); c.setDefaultModel(true); return streamChat(prompt,c); }

    private List<ProviderWrapper> providers(ChatExecutionContext c) {
        if (c.isCancelled()) throw new java.util.concurrent.CancellationException();
        if (c.isDefaultModel()) return List.of();
        if (!registry.isModelEnabled(c.getModelCode())) throw new IllegalArgumentException("Model is missing or disabled");
        var routes=registry.getProviders(c.getModelCode());
        boolean images=c.getModelMessages().stream().filter(m->m instanceof dev.langchain4j.data.message.UserMessage).map(m->(dev.langchain4j.data.message.UserMessage)m)
            .flatMap(m->m.contents().stream()).anyMatch(content->content instanceof dev.langchain4j.data.message.ImageContent);
        boolean remote=c.getModelMessages().stream().filter(m->m instanceof dev.langchain4j.data.message.UserMessage).map(m->(dev.langchain4j.data.message.UserMessage)m)
            .flatMap(m->m.contents().stream()).filter(content->content instanceof dev.langchain4j.data.message.ImageContent).map(content->((dev.langchain4j.data.message.ImageContent)content).image())
            .anyMatch(image->image.url()!=null && !image.url().toString().startsWith("data:"));
        if(images) routes=routes.stream().filter(r->r.isVision() && (!c.isToolsEnabled() || r.isVisionTools()) && (!remote || r.getProvider().supportsRemoteImages())).toList();
        if (routes.isEmpty()) throw new IllegalStateException("No enabled route for model: "+c.getModelCode());
        if ("priority".equals(strategy)) return routes;
        if (!"weighted".equals(strategy)) throw new IllegalArgumentException("Unknown routing strategy");
        // Weighted order within each priority tier; each candidate appears once for failover.
        List<ProviderWrapper> ordered=new ArrayList<>();
        var tiers=new TreeMap<Integer,List<ProviderWrapper>>();
        routes.forEach(r -> tiers.computeIfAbsent(r.getPriority(), key -> new ArrayList<>()).add(r));
        for(var remaining:tiers.values()) while(!remaining.isEmpty()) {
            long total=remaining.stream().mapToLong(ProviderWrapper::getWeight).sum();
            long choice=draw.applyAsLong(total);
            int selected=0;
            for(;selected<remaining.size()-1;selected++) { choice-=remaining.get(selected).getWeight();if(choice<0) break; }
            ordered.add(remaining.remove(selected));
        }
        return ordered;
    }
    @Override public String chat(String prompt, ChatExecutionContext c) {
        var routes=providers(c);
        LLMCallOptions options=options(c);
        if (c.isDefaultModel()) { c.setProviderCode("default"); return invoke(fallbackClient,prompt,c,options); }
        Exception last=null;
        for (var route:routes) {
            for(int retry=0;retry<=route.getMaxRetry();retry++) {
                if (c.isCancelled()) throw new java.util.concurrent.CancellationException();
                var permit=route.acquire(); if (permit==null) break;
                try {
                    String answer=invoke(route.getProvider(),prompt,c,options);
                    permit.success(); c.setProviderCode(route.getProvider().getProviderCode()); return answer;
                } catch (java.util.concurrent.CancellationException e) { permit.cancel(); throw e; }
                catch (Exception e) { permit.failure(); last=e; }
            }
        }
        throw new IllegalStateException("All model routes failed",last);
    }
    private String invoke(LLMClient client,String prompt,ChatExecutionContext c,LLMCallOptions options) {
        c.beginModelCall();
        return c.getModelMessages().isEmpty() ? client.chat(prompt,options,c.getImages()) : client.chatMessages(c.getModelMessages(),options);
    }
    private Flux<String> invokeStream(LLMClient client,String prompt,ChatExecutionContext c,LLMCallOptions options) {
        c.beginModelCall();
        return c.getModelMessages().isEmpty() ? client.streamChat(prompt,options,c.getImages()) : client.streamMessages(c.getModelMessages(),options);
    }
    @Override public Flux<String> streamChat(String prompt,ChatExecutionContext c) {
        return Flux.defer(() -> {
            var routes=providers(c);
            if (c.isDefaultModel()) {
                c.setProviderCode("default"); return invokeStream(fallbackClient,prompt,c,options(c));
            }
            return attempt(routes,0,0,prompt,c);
        });
    }
    private Flux<String> attempt(List<ProviderWrapper> routes,int position,int retry,String prompt,ChatExecutionContext c) {
        return Flux.defer(() -> {
            if (c.isCancelled()) return Flux.error(new java.util.concurrent.CancellationException());
            if (position>=routes.size()) return Flux.error(new IllegalStateException("All model routes failed"));
            var route=routes.get(position);
            var permit=route.acquire(); if (permit==null) return attempt(routes,position+1,0,prompt,c);
            AtomicBoolean emitted=new AtomicBoolean();
            return Flux.defer(() -> invokeStream(route.getProvider(),prompt,c,options(c)))
                .doOnNext(token -> { emitted.set(true); c.setProviderCode(route.getProvider().getProviderCode()); })
                .doOnComplete(permit::success)
                .doOnCancel(permit::cancel)
                .onErrorResume(error -> {
                    if (error instanceof java.util.concurrent.CancellationException) { permit.cancel(); return Flux.error(error); }
                    permit.failure();
                    if (emitted.get() || c.isCancelled()) return Flux.error(error);
                    return retry<route.getMaxRetry() ? attempt(routes,position,retry+1,prompt,c) : attempt(routes,position+1,0,prompt,c);
                });
        });
    }
    private LLMCallOptions options(ChatExecutionContext c) {
        LLMCallOptions options=new LLMCallOptions(); options.setUsageConsumer(c::recordUsage);
        options.setModelName(c.getModelCode()); options.setMaxTokens(c.getMaxOutputTokens());
        if (c.getDefaultTemperature()!=null) options.setTemperature(c.getDefaultTemperature().doubleValue());
        if (c.getDefaultTopP()!=null) options.setTopP(c.getDefaultTopP().doubleValue());
        if (c.getDefaultConfig()!=null && !c.getDefaultConfig().isBlank()) {
            try {
                var json=this.json.readTree(c.getDefaultConfig());
                if (json.has("model_name")) {
                    var name = json.get("model_name");
                    if (!name.isTextual() || name.asText().isBlank())
                        throw new IllegalArgumentException("model_name must be a non-empty string");
                    options.setModelName(name.asText().trim());
                }
                if (json.hasNonNull("seed")) options.setSeed(json.get("seed").asInt());
                if (json.hasNonNull("presence_penalty")) options.setPresencePenalty(json.get("presence_penalty").asDouble());
                if (json.hasNonNull("frequency_penalty")) options.setFrequencyPenalty(json.get("frequency_penalty").asDouble());
                if (json.hasNonNull("response_format")) {
                    var format=json.get("response_format");
                    options.setResponseFormat(format.isTextual()?format.asText():format.path("type").asText());
                }
                if (json.hasNonNull("stop")) {
                    var stop=json.get("stop"); List<String> stops=new ArrayList<>();
                    if (stop.isTextual()) stops.add(stop.asText()); else stop.forEach(s -> stops.add(s.asText()));
                    options.setStop(stops);
                }
            } catch (Exception e) { throw new IllegalArgumentException("Invalid model default_config",e); }
        }
        return options;
    }
}
