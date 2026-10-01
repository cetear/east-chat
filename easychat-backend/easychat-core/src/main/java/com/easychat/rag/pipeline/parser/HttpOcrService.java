package com.easychat.rag.pipeline.parser;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Component
public class HttpOcrService {
    @org.springframework.beans.factory.annotation.Autowired private com.easychat.integration.JsonHttpService http;
    @Value("${easychat.ocr.url:}") private String endpoint;
    @Value("${easychat.ocr.token:}") private String token;
    public boolean configured() {return endpoint!=null&&!endpoint.isBlank();}
    public String recognize(byte[] image) {
        var result=http.post(endpoint,token,Map.of("imageBase64",Base64.getEncoder().encodeToString(image),"mimeType","image/png"));
        if(!result.path("text").isTextual()) throw new IllegalStateException("OCR response must contain text");
        return result.get("text").asText();
    }
}