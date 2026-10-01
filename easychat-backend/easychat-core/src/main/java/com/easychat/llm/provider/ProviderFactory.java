package com.easychat.llm.provider;
import com.easychat.common.domain.model.ModelDefinition;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
/** Register one Bean per protocol. Credentials remain in server-side configuration. */
public interface ProviderFactory {
    String protocol();
    LLMProvider create(ProviderAccount provider, ModelDefinition model, ModelRoute route);
}