package com.easychat.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class EsDocumentRequest {
    private String index;
    private String id;
    private Map<String, Object> document;
}
