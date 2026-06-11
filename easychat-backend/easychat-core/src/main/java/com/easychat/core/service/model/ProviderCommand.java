package com.easychat.core.service.model;

import lombok.Data;

@Data
public class ProviderCommand {
    private String providerCode;
    private String baseUrl;
    private String apiKey;
    private Integer enabled;
}
