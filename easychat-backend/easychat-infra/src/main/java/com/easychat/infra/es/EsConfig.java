package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class EsConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.elasticsearch")
    public EsProperties esProperties() {
        return new EsProperties();
    }

    @Bean
    public RestClient restClient(EsProperties properties) {
        String[] uris = properties.getUris().split(",");
        HttpHost[] hosts = new HttpHost[uris.length];
        for (int i = 0; i < uris.length; i++) {
            String uri = uris[i].trim();
            hosts[i] = HttpHost.create(uri);
        }
        return RestClient.builder(hosts).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Data
    public static class EsProperties {
        private String uris;
    }
}
