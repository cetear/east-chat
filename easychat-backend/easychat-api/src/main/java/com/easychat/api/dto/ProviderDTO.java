package com.easychat.api.dto;

import lombok.Data;

@Data
public class ProviderDTO {

    private String providerCode;
    private String baseUrl;
    private String apiKey;
    private Integer enabled;
}
