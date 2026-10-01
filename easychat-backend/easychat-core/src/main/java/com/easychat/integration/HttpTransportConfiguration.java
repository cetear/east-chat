package com.easychat.integration;
import org.springframework.context.annotation.*;
import java.net.http.HttpClient;
import java.time.Duration;
@Configuration public class HttpTransportConfiguration {
 @Bean public HttpClient configuredHttpClient(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();}
}
