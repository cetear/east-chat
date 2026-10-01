package com.easychat.llm.provider;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.entity.*;
import org.springframework.stereotype.Component;
@Component
@lombok.RequiredArgsConstructor
public class OllamaProviderFactory implements ProviderFactory {
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final java.net.http.HttpClient client;
    public String protocol() {return "ollama";}
    public LLMProvider create(ProviderAccount provider,ModelDefinition model,ModelRoute route) {
        return new OllamaProvider(provider.getProviderCode(),provider.getBaseUrl(),provider.getApiKey(),route.getModelCode(),route.getTimeoutMs()==null?60000:route.getTimeoutMs(),json,client);
    }
}