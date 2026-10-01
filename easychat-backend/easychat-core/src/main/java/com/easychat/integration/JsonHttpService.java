package com.easychat.integration;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
/** Small configured-service transport. Never accepts an endpoint from model/user input. */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class JsonHttpService {
    private final ObjectMapper json;
    private final HttpClient client;
    public JsonNode post(String endpoint,String token,Object body) {
        if(endpoint==null || endpoint.isBlank()) throw new IllegalStateException("Service endpoint is not configured");
        try {
            var builder=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/json");
            if(token!=null && !token.isBlank()) builder.header("Authorization","Bearer "+token);
            var response=com.easychat.common.util.BoundedHttp.send(client,
                    builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),4*1024*1024,Duration.ofSeconds(60));
            if(response.statusCode()/100!=2) throw new IllegalStateException("Configured service returned HTTP "+response.statusCode());
            return json.readTree(response.body());
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}
        catch(java.io.IOException e) {throw new IllegalStateException("Configured service unavailable",e);}
    }
}
