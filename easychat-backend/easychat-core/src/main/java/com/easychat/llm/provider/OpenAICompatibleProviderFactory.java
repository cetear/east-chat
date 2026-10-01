package com.easychat.llm.provider;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.entity.*;
import org.springframework.stereotype.Component;
@Component
public class OpenAICompatibleProviderFactory implements ProviderFactory {
    public String protocol() { return "openai-compatible"; }
    public LLMProvider create(ProviderAccount provider, ModelDefinition model, ModelRoute route) {
        return new OpenAICompatibleProvider(provider.getProviderCode(),provider.getApiKey(),provider.getBaseUrl(),
            route.getModelCode(), model.getDefaultTemperature()==null?null:model.getDefaultTemperature().doubleValue(),
            model.getDefaultTopP()==null?null:model.getDefaultTopP().doubleValue(),model.getMaxOutputTokens(),
            route.getTimeoutMs()==null?60000:route.getTimeoutMs());
    }
}