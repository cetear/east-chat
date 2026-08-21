package com.easychat.infra.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import org.springframework.context.annotation.Primary;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.security.cert.X509Certificate;

@Configuration
public class EsConfig {

    @Value("${spring.elasticsearch.uris}")
    private String uris;

    @Value("${spring.elasticsearch.username}")
    private String username;

    @Value("${spring.elasticsearch.password}")
    private String password;

    @Bean
    @Primary
    public ElasticsearchClient elasticsearchClient() throws Exception {
        URI uri = new URI(uris);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 9200;
        String scheme = uri.getScheme() != null ? uri.getScheme() : "https";

        // 1. 认证信息
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
        );

        // 2. 创建信任所有证书的 SSLContext（仅开发测试用）
        SSLContext sslContext = createTrustAllSSLContext();

        // 3. 构建 RestClient
        RestClient restClient = RestClient.builder(
                        new HttpHost(host, port, scheme)
                )
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider)
                                .setSSLContext(sslContext)
                                .setSSLHostnameVerifier((hostname, session) -> true)  // 跳过主机名验证
                )
                .build();

        // 4. 创建 Transport 和 Client
        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }

    private SSLContext createTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }
}
